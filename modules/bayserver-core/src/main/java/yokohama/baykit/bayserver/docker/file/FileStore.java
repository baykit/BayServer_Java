package yokohama.baykit.bayserver.docker.file;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.util.RoughTime;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class FileStore {

    public class FileContentStatus {
        static final int STARTED = 1;
        static final int READING = 2;
        static final int COMPLETED = 3;
        static final int EXCEEDED = 4;

        public FileContent fileContent;
        public int status;

        FileContentStatus(FileContent fileContent, int status) {
            this.fileContent = fileContent;
            this.status = status;
        }
    }

    private final LinkedHashMap<String, FileContent> contents = new LinkedHashMap<>();
    public final long limitBytes;
    private long totalBytes = 0;
    private final int lifespanMilliSec;

    public FileStore(int timeoutSec, long limitBytes) {
        this.lifespanMilliSec = timeoutSec * 1000;
        this.limitBytes = limitBytes;
    }

    public synchronized FileContentStatus get(File file, boolean[] reading) {
        String path = file.getPath();
        int status = 0;
        FileContent fileContent = contents.get(path);

        if (fileContent != null) {
            long now =  RoughTime.currentTimeMillis();
            if (fileContent.loadedTime + lifespanMilliSec < RoughTime.currentTimeMillis()) {
                totalBytes -= fileContent.content.capacity();
                BayLog.debug("Remove expired content: %s", path);
                contents.remove(path);
                fileContent = null;
            }
            else {
                if(fileContent.isLoaded()) {
                    status = FileContentStatus.COMPLETED;
                }
                else {
                    status = FileContentStatus.READING;
                }
            }
        }

        if(fileContent == null) {
            long len = file.length();
            boolean exceeded = false;
            if (len <= limitBytes) {
                if(totalBytes + len > limitBytes) {
                    if(!evict()) {
                        exceeded = true;
                    }
                }
            }
            else {
                exceeded = true;
            }

            if(exceeded) {
                status = FileContentStatus.EXCEEDED;
            }
            else {
                fileContent = new FileContent(file, (int)len);
                contents.put(path, fileContent);
                totalBytes += len;
                status = FileContentStatus.STARTED;
            }
        }
        return new FileContentStatus(fileContent, status);
    }

    boolean evict() {
        Iterator<Map.Entry<String, FileContent>> iterator = contents.entrySet().iterator();

        boolean evicted = false;
        while (iterator.hasNext()) {
            Map.Entry<String, FileContent> entry = iterator.next();

            if(!entry.getValue().isLoaded()) {
                continue;
            }

            if (entry.getValue().loadedTime + lifespanMilliSec < RoughTime.currentTimeMillis()) {
                // Timed out content
                BayLog.debug("Remove expired content: %s", entry.getKey());
                totalBytes -= entry.getValue().content.capacity();
                iterator.remove();
                evicted = true;
            }
            else {
                break;
            }
        }
        return evicted;
    }




}
