package yokohama.baykit.bayserver.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

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


    public static Socket[] socketPair() throws IOException {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();

        Socket clientSocket = new Socket("localhost", port);
        Socket serverSideSocket = serverSocket.accept();

        serverSocket.close();

        return new Socket[] { clientSocket, serverSideSocket };
    }

    public static SocketChannel[] socketChannelPair() throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress("localhost", 0));
        int port = serverSocketChannel.socket().getLocalPort();

        SocketChannel ch1 = SocketChannel.open(new InetSocketAddress("localhost", port));
        SocketChannel ch2 = serverSocketChannel.accept();

        serverSocketChannel.close();

        return new SocketChannel[] {ch1, ch2};
    }

    public static AsynchronousSocketChannel[] asynchronousSocketChannelPair() throws IOException {
        AsynchronousServerSocketChannel serverChannel = AsynchronousServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress("localhost", 0));
        int port = ((InetSocketAddress)serverChannel.getLocalAddress()).getPort();

        try {
            AsynchronousSocketChannel ch1 = AsynchronousSocketChannel.open();
            Future <Void> connectFuture = ch1.connect(new InetSocketAddress("localhost", port));
            connectFuture.get();

            Future<AsynchronousSocketChannel> acceptFuture = serverChannel.accept();
            AsynchronousSocketChannel ch2 = acceptFuture.get();

            return new AsynchronousSocketChannel[] {ch1, ch2};

        }
        catch (InterruptedException |ExecutionException e) {
            throw new IOException(e);
        }
        finally {
            serverChannel.close();
        }

    }
}
