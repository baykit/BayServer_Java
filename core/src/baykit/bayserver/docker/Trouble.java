package baykit.bayserver.docker;

public interface Trouble extends Docker {

    enum Method {
        GUIDE,
        TEXT,
        REROUTE
    }

    class Command {
        public final Method method;
        public final String target;

        public Command(Method method, String target) {
            this.method = method;
            this.target = target;
        }
    }

    Command find(int status);
}
