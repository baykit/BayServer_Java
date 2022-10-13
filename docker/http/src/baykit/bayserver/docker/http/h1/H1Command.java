package baykit.bayserver.docker.http.h1;

import baykit.bayserver.protocol.Command;

public abstract class H1Command extends Command<H1Command, H1Packet, H1Type, H1CommandHandler> {

    public H1Command(H1Type type) {
        super(type);
    }
}
