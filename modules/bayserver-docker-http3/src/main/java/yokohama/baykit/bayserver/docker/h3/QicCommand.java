package yokohama.baykit.bayserver.docker.h3;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.Command;

import java.io.IOException;

public abstract class QicCommand extends Command<QicCommand, QicCommandPacket, QicCommandType, QicInboundHandler> {

    public QicCommand(QicCommandType type) {
        super(type);
    }

    @Override
    public void unpack(QicCommandPacket packet) {

    }

    @Override
    public void pack(QicCommandPacket packet) {

    }

    @Override
    public NextSocketAction handle(QicInboundHandler handler) throws IOException {
        return null;
    }
}
