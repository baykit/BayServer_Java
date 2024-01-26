package yokohama.baykit.bayserver.rudder;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.AsynchronousServerSocketChannel;

public class AsynchronousServerSocketChannelRudder extends NetworkChannelRudder {

    public AsynchronousServerSocketChannelRudder(AsynchronousServerSocketChannel ch) {
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

    public static AsynchronousServerSocketChannel getAsynchronousServerSocketChannel(Rudder rd) {
        return (AsynchronousServerSocketChannel)((ChannelRudder)rd).channel;
    }
}
