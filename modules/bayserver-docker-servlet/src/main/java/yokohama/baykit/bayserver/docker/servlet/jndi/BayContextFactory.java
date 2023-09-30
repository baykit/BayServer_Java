package yokohama.baykit.bayserver.docker.servlet.jndi;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import java.util.Hashtable;

public class BayContextFactory implements InitialContextFactory {

    static BayContext ctx = new BayContext();

    @Override
    public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
        return ctx;
    }
}
