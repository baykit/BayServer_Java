package baykit.bayserver.docker.h3;

import baykit.bayserver.agent.NextSocketAction;
import baykit.bayserver.protocol.Command;

import java.io.IOException;

public class QicCommand extends Command<QicCommand, QicPacket, QicType, QicCommandHandler> {

    public QicCommand(QicType type) {
        super(type);
    }

    @Override
    public void unpack(QicPacket packet) throws IOException {

    }

    @Override
    public void pack(QicPacket packet) throws IOException {

    }

    @Override
    public NextSocketAction handle(QicCommandHandler handler) throws IOException {
        return null;
    }
}
