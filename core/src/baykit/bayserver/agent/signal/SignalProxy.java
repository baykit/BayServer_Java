package baykit.bayserver.agent.signal;

import baykit.bayserver.BayLog;
import baykit.bayserver.BayMessage;
import baykit.bayserver.Symbol;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class SignalProxy {

    Class signalClass;
    Class signalHandlerClass;

    private SignalProxy(Class signalClass, Class signalHandlerClass) {
        this.signalClass = signalClass;
        this.signalHandlerClass = signalHandlerClass;
    }

    public static SignalProxy getProxy() {
        try {
            return new SignalProxy(
                    Class.forName("sun.misc.Signal"),
                    Class.forName("sun.misc.SignalHandler"));
        }
        catch(ClassNotFoundException e) {
            BayLog.info("Cannot use signal: %s", e);
        }
        return null;
    }

    public Object createSignal(String sig) throws Exception {
        Constructor m = signalClass.getDeclaredConstructor(String.class);
        return m.newInstance(sig);
    }

    public Object createSignalHandler(SignalHandler handler) {
        return Proxy.newProxyInstance(
                signalHandlerClass.getClassLoader(),
                new Class[]{signalHandlerClass},
                (proxy, method, args) -> {
                    handler.handle();
                    return null;
                });
    }


    void register(String sig, SignalHandler handler) {
        try {
            Object signal = createSignal(sig);
            Method m = signalClass.getMethod("handle", signalClass, signalHandlerClass);
            m.invoke(null, createSignal(sig), createSignalHandler(handler));
        }
        catch(Exception e) {
            BayLog.error(BayMessage.get(Symbol.INT_CANNOT_SET_SIG_HANDLER, e));
            BayLog.error(e);
        }
    }

}
