package yokohama.baykit.bayserver.protocol;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.util.ClassUtil;
import yokohama.baykit.bayserver.util.DataConsumeListener;
import yokohama.baykit.bayserver.util.Reusable;
import yokohama.baykit.bayserver.ship.Ship;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class ProtocolHandler<C extends Command<C, P, T, ?>, P extends Packet<T>, T>
        implements Reusable {

    public final PacketUnpacker<P> packetUnpacker;
    public final PacketPacker<P> packetPacker;
    public final CommandUnPacker<P> commandUnpacker;
    public final CommandPacker<C, P, T, ?> commandPacker;
    public final CommandHandler<C>  commandHandler;
    public final boolean serverMode;
    public Ship ship;

    public ProtocolHandler(
            PacketUnpacker<P> packetUnpacker,
            PacketPacker<P> packetPacker,
            CommandUnPacker<P> commandUnpacker,
            CommandPacker<C, P, T, ?> commandPacker,
            CommandHandler<C> commandHandler, boolean serverMode) {
        this.packetUnpacker = packetUnpacker;
        this.packetPacker = packetPacker;
        this.commandUnpacker = commandUnpacker;
        this.commandPacker = commandPacker;
        this.commandHandler = commandHandler;
        this.serverMode = serverMode;
    }

    @Override
    public String toString() {
        return ClassUtil.getLocalName(getClass()) + " ship=" + ship;
    }

    public void init(Ship ship) {
        this.ship = ship;
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
        commandHandler.reset();
        ship = null;
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
