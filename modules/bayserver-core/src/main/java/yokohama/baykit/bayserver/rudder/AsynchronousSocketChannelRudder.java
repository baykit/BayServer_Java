package yokohama.baykit.bayserver.rudder;

import yokohama.baykit.bayserver.util.IOUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;

public class AsynchronousSocketChannelRudder extends NetworkChannelRudder {

    public AsynchronousSocketChannelRudder(AsynchronousSocketChannel ch) {
        super(ch);
    }

    ////////////////////////////////////////////
    // Implements Rudder
    ////////////////////////////////////////////

    @Override
    public void setNonBlocking() throws IOException {

    }

    ////////////////////////////////////////////
    // Implements ChannelRudder
    ////////////////////////////////////////////

    @Override
    public int read(ByteBuffer buf) throws IOException {
        try {
            return ((AsynchronousSocketChannel) channel).read(buf).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        }
    }

    @Override
    public int write(ByteBuffer buf) throws IOException {
        try {
            return ((AsynchronousSocketChannel) channel).write(buf).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException(e);
        }
    }

    ////////////////////////////////////////////
    // Implements NetChannelRudder
    ////////////////////////////////////////////

    @Override
    public int getRemotePort() throws IOException {
        return ((InetSocketAddress) ((AsynchronousSocketChannel) channel).getRemoteAddress()).getPort();
    }

    @Override
    public InetAddress getRemoteAddress() throws IOException {
        return ((InetSocketAddress) ((AsynchronousSocketChannel) channel).getRemoteAddress()).getAddress();
    }

    @Override
    public InetAddress getLocalAddress() throws IOException {
        return ((InetSocketAddress) ((AsynchronousSocketChannel) channel).getLocalAddress()).getAddress();
    }

    @Override
    public int getSocketReceiveBufferSize() throws IOException {
        return  IOUtil.getSockRecvBufSize((AsynchronousSocketChannel) channel);
    }

    ////////////////////////////////////////////
    // Static methods
    ////////////////////////////////////////////

    public static AsynchronousSocketChannel getAsynchronousSocketChannel(Rudder rd) {
        return (AsynchronousSocketChannel)((ChannelRudder)rd).channel;
    }
}
