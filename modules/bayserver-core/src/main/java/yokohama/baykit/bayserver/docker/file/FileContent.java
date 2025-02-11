package yokohama.baykit.bayserver.docker.file;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.RoughTime;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class FileContent {
    public File path;
    public ByteBuffer content;
    public long loadedTime;
    private ArrayList<Rudder> waiters = new ArrayList<>();

    public FileContent(File path, int length) {
        this.path = path;
        this.content = ByteBuffer.allocate(length);
        this.loadedTime = RoughTime.currentTimeMillis();
    }

    public boolean isLoaded() {
        return !content.hasRemaining();
    }

    public synchronized void addWaiter(Rudder waiter) {
        if (isLoaded())
            wakeupWaiter(waiter);
        else
            waiters.add(waiter);
    }

    public synchronized void complete() {
        for (Rudder waiter : waiters) {
            wakeupWaiter(waiter);
        }
        waiters.clear();
    }

    private void wakeupWaiter(Rudder waiter) {
        ByteBuffer buf = ByteBuffer.allocate(1);
        try {
            waiter.write(buf);
        } catch (IOException e) {
            BayLog.error(e, "Write error: %s", e);
        }

    }
}
