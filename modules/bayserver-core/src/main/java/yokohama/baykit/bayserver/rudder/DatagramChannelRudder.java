package yokohama.baykit.bayserver.rudder;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class DatagramChannelRudder extends NetworkChannelRudder {

    public DatagramChannelRudder(DatagramChannel ch) {
        super(ch);
    }

    ////////////////////////////////////////////
    // Implements Rudder
    ////////////////////////////////////////////

    @Override
    public void setNonBlocking() throws IOException {
        ((DatagramChannel)channel).configureBlocking(false);
    }

    @Override
    public int read(ByteBuffer buf) throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public int write(ByteBuffer buf) throws IOException {
        throw new IOException("Not supported");
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

    public static DatagramChannel getDataGramChannel(Rudder rd) {
        return (DatagramChannel) ((NetworkChannelRudder)rd).channel;
    }
}
