package yokohama.baykit.bayserver.tour;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.rudder.ReadableByteChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.DirectoryException;
import yokohama.baykit.bayserver.util.RoughTime;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DirectBoardingStore caches file descriptors for Direct Boarding (sendfile/transferTo).
 *
 * Note: In Java, FileChannel.transferTo() internally calls fstat on every invocation,
 * which negates the expected zero-copy performance benefit. The Barge Docker
 * (in-memory content caching) is recommended instead. This class is retained
 * as a reference for porting to other languages where the equivalent API may
 * perform better.
 */
public class DirectBoardingStore {

    public static class FileInfo {

        String fileName;
        Rudder rudder;
        int fileLength;
        long lastAccessTime;

        public FileInfo(String fileName, Rudder rd, int len) {
            this.fileName = fileName;
            this.rudder = rd;
            this.fileLength = len;
            access();
        }

        public void access() {
            lastAccessTime = RoughTime.currentTimeMillis();
        }

        public void close() {
            try {
                if(rudder != null)
                    rudder.close();
            } catch (IOException e) {
                BayLog.error(e);
            }
            rudder = null;
        }
    }

    private static DirectBoardingStore directBoardingStore;

    // Enable "Access Order" (LRU) mode by setting the 3rd argument to true.
    // In this mode, the most recently accessed entry moves to the end of the list.
    private final LinkedHashMap<String, FileInfo> files = new LinkedHashMap<>(
            16, 0.75f, true) {


        @Override
        protected boolean removeEldestEntry(Map.Entry<String, FileInfo> eldest) {
            // Determine if the cache has exceeded its maximum allowed capacity.
            boolean shouldRemove = size() > maxCargos;

            if (shouldRemove) {
                // Evict the least recently used (LRU) file descriptor and ensure it's closed.
                // This prevents resource leaks by releasing the OS-level file descriptor.
                eldest.getValue().close();
            }
            return shouldRemove;
        }
    };

    public final long maxCargos;
    private final int lifespanMilliSec;
    private final int maxCargoSize;

    private DirectBoardingStore(int timeoutSec, int maxCargos, int maxCargoSize) {
        this.lifespanMilliSec = timeoutSec * 1000;
        this.maxCargos = maxCargos;
        this.maxCargoSize = maxCargoSize;
    }

    public static synchronized FileInfo getFileInfo(String path) throws IOException {
        return getDirectBoardingStore().get(path);
    }


    ////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////

    private FileInfo get(String path) throws IOException{
        FileInfo info = files.get(path);
        if(info != null && RoughTime.currentTimeMillis() > info.lastAccessTime + lifespanMilliSec) {
            BayLog.debug("%d %d %d %d", info.lastAccessTime, lifespanMilliSec, info.lastAccessTime + lifespanMilliSec, RoughTime.currentTimeMillis());
            info.close();
            files.remove(path);
            info = null;
        }

        if(info == null) {
            if (Files.isDirectory(Path.of(path))) {
                throw new DirectoryException();
            }

            long size = Files.size(Path.of(path));
            if (size > BayServer.harbor.maxCargoSize() * 1024 * 1024) {
                info = new DirectBoardingStore.FileInfo(path, null, (int) size);
            }
            else {
                FileChannel ch = FileChannel.open(Path.of(path));
                Rudder rd = new ReadableByteChannelRudder(ch);
                info = new DirectBoardingStore.FileInfo(path, rd, (int) size);
            }

            files.put(path, info);
        }

        info.access();
        return info;
    }


    private static DirectBoardingStore getDirectBoardingStore() {
         if(directBoardingStore == null)
             directBoardingStore = new DirectBoardingStore(
                     BayServer.harbor.cargoLifespanSec(),
                     BayServer.harbor.maxDirectBoardings(),
                     BayServer.harbor.maxCargoSize() * 1024 * 1024);

         return directBoardingStore;
    }
}
