package yokohama.baykit.bayserver.util;

import java.io.IOException;

public class BlockingIOException extends IOException {

    public BlockingIOException(String msg) {
        super(msg);
    }
}
