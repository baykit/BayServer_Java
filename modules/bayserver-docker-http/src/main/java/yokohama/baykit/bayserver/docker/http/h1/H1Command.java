package yokohama.baykit.bayserver.docker.http.h1;

import yokohama.baykit.bayserver.protocol.Command;

public abstract class H1Command extends Command<H1Command, H1Packet, H1CommandHandler> {

    public H1Command(int type) {
        super(type);
    }
}
