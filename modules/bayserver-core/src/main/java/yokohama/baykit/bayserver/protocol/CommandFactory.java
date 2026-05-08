package yokohama.baykit.bayserver.protocol;

import java.util.function.IntFunction;

public abstract class CommandFactory<C extends Command> {
    public abstract C createCommand(int type);
}
