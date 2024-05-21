package yokohama.baykit.bayserver.rudder;

import yokohama.baykit.bayserver.util.IOUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SocketChannel;

public class SocketChannelRudder extends NetworkChannelRudder {

    public SocketChannelRudder(SocketChannel channel) {
        super(channel);
    }

    ////////////////////////////////////////////
    // Implements Rudder
    ////////////////////////////////////////////

    @Override
    public void setNonBlocking() throws IOException {
        ((SocketChannel)channel).configureBlocking(false);
    }

    ////////////////////////////////////////////
    // Implements ChannelRudder
    ////////////////////////////////////////////

    @Override
    public int getRemotePort() throws IOException {
        return ((SocketChannel)channel).socket().getPort();
    }

    @Override
    public InetAddress getRemoteAddress() throws IOException {
        return ((SocketChannel)channel).socket().getInetAddress();
    }

    @Override
    public InetAddress getLocalAddress() throws IOException {
        return ((SocketChannel)channel).socket().getLocalAddress();
    }

    @Override
    public int getSocketReceiveBufferSize() throws IOException {
        return  IOUtil.getSockRecvBufSize((SocketChannel) channel);
    }

    ////////////////////////////////////////////
    // Static methods
    ////////////////////////////////////////////

    public static SocketChannel socketChannel(Rudder rd) {
        return (SocketChannel)((ChannelRudder)rd).channel;
    }
}
