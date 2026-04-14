package yokohama.baykit.bayserver.docker.file;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.util.RoughTime;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class FileStore {

    // Enable "Access Order" (LRU) mode by setting the 3rd argument to true.
    // In this mode, the most recently accessed entry moves to the end of the list.
    private final LinkedHashMap<Path, FileSendInfo> files = new LinkedHashMap<Path, FileSendInfo>(
            16, 0.75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<Path, FileSendInfo> eldest) {
            // Determine if the cache has exceeded its maximum allowed capacity.
            boolean shouldRemove = size() > maxFiles;

            if (shouldRemove) {
                // Evict the least recently used (LRU) file descriptor and ensure it's closed.
                // This prevents resource leaks by releasing the OS-level file descriptor.
                eldest.getValue().close();
            }
            return shouldRemove;
        }
    };

    public final long maxFiles;
    private final int lifespanMilliSec;

    public FileStore(int timeoutSec, int maxFiles) {
        this.lifespanMilliSec = timeoutSec * 1000;
        this.maxFiles = maxFiles;
    }

    public FileSendInfo get(Path path) {
        FileSendInfo f = files.get(path);
        if(f != null && RoughTime.currentTimeMillis() > f.lasstAccessTime + lifespanMilliSec) {
            BayLog.info("%d %d %d %d", f.lasstAccessTime, lifespanMilliSec, f.lasstAccessTime + lifespanMilliSec, RoughTime.currentTimeMillis());
            f.close();
            files.remove(path);
            f = null;
        }
        if(f != null)
            f.access();
        return f;
    }


    public void put(Path real, FileSendInfo f) {
        files.put(real, f);
    }
}
