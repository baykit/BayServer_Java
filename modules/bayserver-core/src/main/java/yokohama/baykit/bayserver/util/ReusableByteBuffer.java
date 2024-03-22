package yokohama.baykit.bayserver.util;

import java.nio.ByteBuffer;

public class ReusableByteBuffer implements Reusable {

    public ByteBuffer buffer;

    public ReusableByteBuffer(int capacity) {
        buffer = ByteBuffer.allocate(capacity);
    }

    ////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////
    @Override
    public void reset() {
        buffer.clear();
    }
}
