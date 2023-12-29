package yokohama.baykit.bayserver.protocol;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.common.Postman;
import yokohama.baykit.bayserver.util.ClassUtil;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Reusable;
import yokohama.baykit.bayserver.ship.Ship;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class ProtocolHandler<C extends Command<C, P, T, ?>, P extends Packet<T>, T>
        implements Reusable {

    public PacketUnpacker<P> packetUnpacker;
    public PacketPacker<P> packetPacker;
    public CommandUnPacker<P> commandUnpacker;
    public CommandPacker<C, P, T, ?> commandPacker;
    public PacketStore<P, T> packetStore;
    public boolean serverMode;
    public Ship ship;

    @Override
    public String toString() {
        return ClassUtil.getLocalName(getClass()) + " ship=" + ship;
    }

    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    @Override
    public void reset() {
        commandUnpacker.reset();
        commandPacker.reset();
        packetPacker.reset();
        packetUnpacker.reset();
    }

    /////////////////////////////////////
    // Abstract methods
    /////////////////////////////////////
    public abstract String protocol();

    /**
     * Get max of request data size (maybe not packet size)
     */
    public abstract int maxReqPacketDataSize();

    /**
     * Get max of response data size (maybe not packet size)
     */
    public abstract int maxResPacketDataSize();

    /**
     * Send protocol error to client
     */
    public abstract boolean onProtocolError(ProtocolException e) throws IOException;

    /////////////////////////////////////
    // Other methods
    /////////////////////////////////////

    public NextSocketAction bytesReceived(ByteBuffer buf) throws IOException {
        return packetUnpacker.bytesReceived(buf);
    }

    public final void post(C cmd) throws IOException {
        post(cmd, null);
    }

    public void post(C cmd, DataConsumeListener listener) throws IOException {
        commandPacker.post(ship.postman, cmd, listener);
    }
}
