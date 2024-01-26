package yokohama.baykit.bayserver.rudder;

import yokohama.baykit.bayserver.util.IOUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;

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
