package baykit.bayserver.docker.servlet;

import baykit.bayserver.BayLog;
import baykit.bayserver.ConfigException;
import baykit.bayserver.bcf.BcfElement;
import baykit.bayserver.bcf.BcfKeyVal;
import baykit.bayserver.bcf.BcfObject;
import baykit.bayserver.docker.servlet.jndi.BayContext;
import baykit.bayserver.docker.servlet.jndi.BayContextFactory;
import baykit.bayserver.docker.servlet.jndi.SimpleDataSource;
import baykit.bayserver.util.StringUtil;

import javax.naming.Context;
import javax.naming.NamingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

public class ResourceDocker {

    static String MAIL_SESSION_CLASS = "javax.mail.Session";
    BayContext ctx;
    public static final String JNDI_FACTORY_CLASS= "baykit.bayserver.docker.servlet.jndi.BayContextFactory";

    public ResourceDocker()  {
        try {
            ctx = (BayContext) new BayContextFactory().getInitialContext(null);
        } catch (NamingException e) {
            BayLog.error(e);
        }
    }

    /**
     * Prepare resources in context
     */
    void init(BcfElement elm) throws ConfigException {

        String name = elm.arg;
        String type = elm.getValue(":type");
        Object res;

        if(StringUtil.empty(type))
            throw new ConfigException(
                    elm.fileName,
                    elm.lineNo,
                    ServletMessage.get(ServletSymbol.SVT_RESOURCE_TYPE_NOT_SPECIFIED));

        if(System.getProperty(Context.INITIAL_CONTEXT_FACTORY) == null)
            System.setProperty(Context.INITIAL_CONTEXT_FACTORY, JNDI_FACTORY_CLASS);

        if(type.equalsIgnoreCase("javax.sql.DataSource")) {
            res = createDataSource(elm);
        }
        else if(type.equalsIgnoreCase("javax.mail.Session")) {
            res = createMailSession(elm);
        }
        else
            throw new ConfigException(
                    elm.fileName,
                    elm.lineNo,
                    ServletMessage.get(ServletSymbol.SVT_UNKNOWN_RESOURCE_TYPE, type));

        BayLog.debug("Bind:" + name + "=" + res);

        try {
            ctx.bind("comp/env/" + name, res);
        } catch (NamingException e) {
            BayLog.error(e);
        }
    }

    private Object createDataSource(BcfElement elm) throws ConfigException {
        try {
            return new SimpleDataSource(
                    elm.getValue("user"),
                    elm.getValue("password"),
                    elm.getValue("driver"),
                    elm.getValue("url"));
        } catch (ClassNotFoundException e) {
            throw new ConfigException(
                    elm.fileName,
                    elm.lineNo,
                    ServletMessage.get(ServletSymbol.SVT_CANNOT_CREATE_DATASOURCE, e),
                    e);
        }
    }


    private Object createMailSession(BcfElement elm) throws ConfigException {
        try {
            Class cls = Class.forName(MAIL_SESSION_CLASS);
            Method m = cls.getMethod("getInstance", new Class[]{Properties.class});

            Properties params = new Properties();
            for(BcfObject o: elm.contentList) {
                if(o instanceof BcfKeyVal) {
                    BcfKeyVal kv = (BcfKeyVal) o;
                    params.put(kv.key, kv.value);
                }
            }

            return m.invoke(null, params);

        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new ConfigException(
                    elm.fileName,
                    elm.lineNo,
                    ServletMessage.get(ServletSymbol.SVT_CANNOT_CREATE_MAIL_SESSION, e),
                    e);
        }
    }
}
