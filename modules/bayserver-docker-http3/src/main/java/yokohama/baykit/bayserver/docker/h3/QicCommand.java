package yokohama.baykit.bayserver.docker.h3;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.protocol.Command;

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
