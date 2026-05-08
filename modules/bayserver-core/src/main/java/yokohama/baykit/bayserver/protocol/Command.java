package yokohama.baykit.bayserver.protocol;

import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.util.Reusable;

import java.io.IOException;

public abstract class Command<C extends Command<C, P, H>, P extends Packet, H extends CommandHandler<C>> implements Reusable {

    public int type;

    public Command(int type) {
        this.type = type;
    }

    public abstract void unpack(P packet) throws IOException;

    public abstract void pack(P packet) throws IOException;

    // Call handler (visitor pattern)
    public abstract NextSocketAction handle(H handler) throws IOException;
}
