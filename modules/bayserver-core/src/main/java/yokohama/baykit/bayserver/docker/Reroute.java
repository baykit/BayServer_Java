package yokohama.baykit.bayserver.docker;

public interface Reroute extends Docker {

    String reroute(Town twn, String uri);
}
