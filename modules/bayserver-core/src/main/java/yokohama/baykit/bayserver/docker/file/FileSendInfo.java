package yokohama.baykit.bayserver.docker.file;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.util.RoughTime;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Base64;

public class FileSendInfo {
    public Rudder rudder;
    public int length;
    public long lasstAccessTime;

    public FileSendInfo(Rudder rd, int len) {
        this.rudder = rd;
        this.length = len;
        access();
    }

    public void access() {
        lasstAccessTime = RoughTime.currentTimeMillis();
    }

    public void close() {
        try {
            rudder.close();
        } catch (IOException e) {
            BayLog.error(e);
        }
        rudder = null;
    }
}
