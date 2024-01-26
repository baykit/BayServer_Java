package yokohama.baykit.bayserver.rudder;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.ServerSocketChannel;

public class ServerSocketChannelRudder extends NetworkChannelRudder {

    public ServerSocketChannelRudder(ServerSocketChannel channel) {
        super(channel);
    }


    ////////////////////////////////////////////
    // Implements Rudder
    ////////////////////////////////////////////

    @Override
    public void setNonBlocking() throws IOException {
        ((ServerSocketChannel)channel).configureBlocking(false);
    }

    ////////////////////////////////////////////
    // Implements ChannelRudder
    ////////////////////////////////////////////

    @Override
    public int getRemotePort() throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public InetAddress getRemoteAddress() throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public InetAddress getLocalAddress() throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public int getSocketReceiveBufferSize() throws IOException {
        throw new IOException("Not supported");
    }


    ////////////////////////////////////////////
    // Static methods
    ////////////////////////////////////////////

    public static ServerSocketChannel getServerSocketChannel(Rudder rd) {
        return (ServerSocketChannel)((ChannelRudder)rd).channel;
    }
}
