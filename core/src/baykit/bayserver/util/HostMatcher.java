package baykit.bayserver.util;

public class HostMatcher {
    enum MatchType {
        All,
        Exact,
        Domain
    }

    MatchType type;
    String host;
    String domain;

    public HostMatcher(String host) {
        if (host.equals("*")) {
            type = MatchType.All;
        } else if (host.startsWith("*.")) {
            type = MatchType.Domain;
            this.domain = host.substring(2);
        } else {
            type = MatchType.Exact;
            this.host = host;
        }
    }


    public boolean match(String remoteHost) {
        if (type == MatchType.All) {
            // all match
            return true;
        }
        if (remoteHost == null) {
            return false;
        }

        if (type == MatchType.Exact) {
            // exact match
            return remoteHost.equals(host);
        } else {
            // domain match
            return remoteHost.endsWith(domain);
        }
    }
}
