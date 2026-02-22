package yokohama.baykit.bayserver.docker.h3;

import yokohama.baykit.bayserver.protocol.*;

public class H3ProtocolHandler extends ProtocolHandler {

    public static final int MAX_H3_PACKET_SIZE = 1024;

    public H3ProtocolHandler(CommandHandler cmdHandler) {
        super(null, null, null, null, cmdHandler, true);
    }

    @Override
    public String protocol() {
        return "h3";
    }

    @Override
    public int maxReqPacketDataSize() {
        return MAX_H3_PACKET_SIZE;
    }

    @Override
    public int maxResPacketDataSize() {
        return MAX_H3_PACKET_SIZE;
    }
}
