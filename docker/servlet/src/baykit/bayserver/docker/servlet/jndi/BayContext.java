package baykit.bayserver.docker.servlet.jndi;

import baykit.bayserver.util.StringUtil;

import javax.naming.*;
import java.util.Hashtable;

public class BayContext implements Context {

    String prefix;
    Hashtable<String, Object> bindings;

    public BayContext() {
        prefix = "";
        bindings = new Hashtable<>();
    }

    BayContext(String prefix, Hashtable<String, Object> bindings) {
        this.prefix = prefix;
        this.bindings = bindings;
    }

    void methodNotImplemented() {
        throw new NoSuchMethodError("FIXME: Method not implemented");
    }

    @Override
    public Object lookup(Name name) throws NamingException {
        methodNotImplemented();
        return null;
    }

    @Override
    public Object lookup(String name) throws NamingException {
        if(name.startsWith("java:"))
            name = name.substring(5);

        String fullName = StringUtil.empty(prefix) ? name : (prefix + "/" + name);
        Object res = bindings.get(fullName);
        if(res == null) {
            res = new BayContext(fullName, bindings);
        }
        return res;
    }

    @Override
    public void bind(Name name, Object obj) throws NamingException {
        methodNotImplemented();
    }

    @Override
    public void bind(String name, Object obj) throws NamingException {
        bindings.put(name, obj);
    }

    @Override
    public void rebind(Name name, Object obj) throws NamingException {
        methodNotImplemented();
    }

    @Override
    public void rebind(String name, Object obj) throws NamingException {
        bindings.put(name, obj);
    }

    @Override
    public void unbind(Name name) throws NamingException {
        methodNotImplemented();
    }

    @Override
    public void unbind(String name) throws NamingException {
        bindings.remove(name);
    }

    @Override
    public void rename(Name oldName, Name newName) throws NamingException {
        methodNotImplemented();
    }

    @Override
    public void rename(String oldName, String newName) throws NamingException {
        Object obj = bindings.get(oldName);
        unbind(oldName);
        bind(newName, obj);
    }

    @Override
    public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
        methodNotImplemented();
        return null;
    }

    @Override
    public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
        methodNotImplemented();
        return null;
    }

    @Override
    public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
        methodNotImplemented();
        return null;
    }

    @Override
    public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
        methodNotImplemented();
        return null;
    }

    @Override
    public void destroySubcontext(Name name) throws NamingException {

    }

    @Override
    public void destroySubcontext(String name) throws NamingException {

    }

    @Override
    public Context createSubcontext(Name name) throws NamingException {
        methodNotImplemented();
        return null;
    }

    @Override
    public Context createSubcontext(String name) throws NamingException {
        return new BayContext(prefix + "/" + name, bindings);
    }

    @Override
    public Object lookupLink(Name name) throws NamingException {
        methodNotImplemented();
        return null;
    }

    @Override
    public Object lookupLink(String name) throws NamingException {
        methodNotImplemented();
        return null;
    }

    @Override
    public NameParser getNameParser(Name name) throws NamingException {
        return null;
    }

    @Override
    public NameParser getNameParser(String name) throws NamingException {
        return null;
    }

    @Override
    public Name composeName(Name name, Name prefix) throws NamingException {
        return null;
    }

    @Override
    public String composeName(String name, String prefix) throws NamingException {
        return null;
    }

    @Override
    public Object addToEnvironment(String propName, Object propVal) throws NamingException {
        return null;
    }

    @Override
    public Object removeFromEnvironment(String propName) throws NamingException {
        return null;
    }

    @Override
    public Hashtable<?, ?> getEnvironment() throws NamingException {
        return null;
    }

    @Override
    public void close() throws NamingException {
    }

    @Override
    public String getNameInNamespace() throws NamingException {
        return null;
    }
}
