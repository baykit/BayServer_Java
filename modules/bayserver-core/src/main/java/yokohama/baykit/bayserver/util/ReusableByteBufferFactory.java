package yokohama.baykit.bayserver.util;

public class ReusableByteBufferFactory implements ObjectFactory<ReusableByteBuffer>{

    private int capacity;

    public ReusableByteBufferFactory(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public ReusableByteBuffer createObject() {
        return new ReusableByteBuffer(capacity);
    }
}
