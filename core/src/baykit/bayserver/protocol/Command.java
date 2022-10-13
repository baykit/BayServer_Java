package baykit.bayserver.protocol;

import baykit.bayserver.agent.NextSocketAction;

import java.io.IOException;

public abstract class Command<C extends Command<C, P, T, H>, P extends Packet<T>, T, H extends CommandHandler<C>> {

    public T type;

    public Command(T type) {
        this.type = type;
    }

    public abstract void unpack(P packet) throws IOException;

    public abstract void pack(P packet) throws IOException;

    // Call handler (visitor pattern)
    public abstract NextSocketAction handle(H handler) throws IOException;
}
