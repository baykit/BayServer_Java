package yokohama.baykit.bayserver.util;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

public class IOUtil {

    public static int getSockRecvBufSize(SocketChannel ch) throws IOException {
        return ch.getOption(StandardSocketOptions.SO_RCVBUF);
    }

    public static int getSockRecvBufSize(AsynchronousSocketChannel ch) throws IOException {
        return ch.getOption(StandardSocketOptions.SO_RCVBUF);
    }

    public static int readInt32(ReadableByteChannel ch) throws IOException{
        ByteBuffer b = ByteBuffer.allocate(4);
        int c = ch.read(b);
        if(b.hasRemaining()) {
            throw new BlockingIOException("Read bytes: " + c + "/" + 4);
        }
        b.flip();
        return (((int)b.get() & 0xff) << 24) | (((int)b.get() & 0xff) << 16) | (((int)b.get() & 0xff) << 8) | ((int)b.get() & 0xff);
    }

    public static void writeInt32(WritableByteChannel ch, int val) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(4);
        b.put((byte)(val >> 24));
        b.put((byte)((val >> 16) & 0xff));
        b.put((byte)((val >> 8) & 0xff));
        b.put((byte)(val & 0xff));
        b.flip();
        int c = ch.write(b);
    }
}
