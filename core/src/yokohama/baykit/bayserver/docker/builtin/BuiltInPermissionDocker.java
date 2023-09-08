package yokohama.baykit.bayserver.docker.builtin;

import baykit.bayserver.*;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.Permission;
import yokohama.baykit.bayserver.docker.base.DockerBase;
import baykit.bayserver.util.*;
import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayMessage;
import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.util.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class BuiltInPermissionDocker extends DockerBase implements Permission {

    ArrayList<CheckItem> checkList = new ArrayList<>();
    
    ArrayList<Groups.Group> groups = new ArrayList<>();

    private static class CheckItem {
        PermissionMatcher matcher;
        boolean admit;

        public CheckItem(PermissionMatcher matcher, boolean admit) {
            this.matcher = matcher;
            this.admit = admit;
        }

        public boolean admitted(SocketChannel ch) {
            return matcher.match(ch) == admit;
        }

        public boolean admitted(Tour tour) {
            return matcher.match(tour) == admit;
        }
    }
    
    private interface PermissionMatcher {
        boolean match(SocketChannel ch);
        boolean match(Tour tour);
    }
    
    private static class HostPermissionMatcher implements PermissionMatcher {

        HostMatcher mch;

        public HostPermissionMatcher(String hostPtn) {
            this.mch = new HostMatcher(hostPtn);
        }

        public boolean match(SocketChannel ch)  {
            return mch.match(ch.socket().getInetAddress().getHostName());
        }

        public boolean match(Tour tour) {
            return mch.match(tour.req.remoteHost());
        }
    }

    class IpPermissionMatcher implements PermissionMatcher {

        IpMatcher mch;

        public IpPermissionMatcher(String ipDesc) throws UnknownHostException {
            this.mch = new IpMatcher(ipDesc);
        }

        public boolean match(SocketChannel ch)  {
            return mch.match(ch.socket().getInetAddress());
        }

        public boolean match(Tour tour) {
            try {
                return mch.match(InetAddress.getByName(tour.req.remoteAddress));
            } catch (UnknownHostException e) {
                BayLog.error(e);
                return false;
            }
        }


    }

    /////////////////////////////////////////////////////////////////
    // Override methods
    /////////////////////////////////////////////////////////////////
    @Override
    public void socketAdmitted(SocketChannel ch) throws HttpException {
        // Check remote host
        boolean isOk = true;
        for (CheckItem chk : checkList) {
            if (chk.admit) {
                if (chk.admitted(ch)) {
                    isOk = true;
                    break;
                }
            } else {
                if (!chk.admitted(ch)) {
                    isOk = false;
                    break;
                }
            }
        }

        if (!isOk) {
            BayLog.error("Permission error: socket not admitted: %s", ch);
            throw new HttpException(HttpStatus.FORBIDDEN, "");
        }
    }

    @Override
    public void tourAdmitted(Tour tour) throws HttpException {
        // Check remote host
        boolean isOk = true;
        for(CheckItem chk : checkList) {
            if(chk.admit) {
                if(chk.admitted(tour)) {
                    isOk = true;
                    break;
                }
            }
            else {
                if(!chk.admitted(tour)) {
                    isOk = false;
                    break;
                }
            }
        }
        
        if(!isOk)
            throw new HttpException(HttpStatus.FORBIDDEN, tour.req.uri);
        
        if(groups.isEmpty()) 
            return;
        
        // Check member         
        isOk = false;
        if(tour.req.remoteUser != null) {
            for(Groups.Group g : groups) {
                if(g.validate(tour.req.remoteUser, tour.req.remotePass)) {
                    isOk = true;
                    break;
                }
            }
        }

        if(!isOk) {
            tour.res.headers.set(Headers.WWW_AUTHENTICATE, "Basic realm=\"Auth\"");
            throw new HttpException(HttpStatus.UNAUTHORIZED, "");
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Implements DockerBase
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);
    }

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        try {
            switch(kv.key.toLowerCase()) {
                default:
                    return false;

                case "admit":
                case "allow": {
                    for (PermissionMatcher pm : parseValue(kv)) {
                        checkList.add(new CheckItem(pm, true));
                    }
                    break;
                }

                case "refuse":
                case "deny": {
                    for (PermissionMatcher pm : parseValue(kv)) {
                        checkList.add(new CheckItem(pm, false));
                    }
                    break;
                }

                case "group": {
                    StringTokenizer st = new StringTokenizer(kv.value);
                    while (st.hasMoreTokens()) {
                        Groups.Group g = Groups.getGroup(st.nextToken());
                        if (g == null) {
                            throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.CFG_GROUP_NOT_FOUND(kv.value));
                        }
                        groups.add(g);
                    }
                    break;
                }
            }
            return true;
        }
        catch(ConfigException e) {
            throw e;
        }
        catch(Exception e) {
            throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.CFG_INVALID_PERMISSION_DESCRIPTION(kv.value), e);
        }
    }

    /////////////////////////////////////////////////////////////////
    // Private methods
    /////////////////////////////////////////////////////////////////

    private List<PermissionMatcher> parseValue(BcfKeyVal kv) throws Exception {
        StringTokenizer st = new StringTokenizer(kv.value);
        String type = null;
        List<String> matchStr = new ArrayList<>();

        if(st.hasMoreElements()) {
            type = st.nextToken();
            while (st.hasMoreElements())
                matchStr.add(st.nextToken());
        }
        
        if(matchStr.isEmpty()) {
            throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.CFG_INVALID_PERMISSION_DESCRIPTION(kv.value));
        }

        List<PermissionMatcher> pmList = new ArrayList<>();
        if(type.equalsIgnoreCase("host")) {
            for(String m : matchStr) {
                pmList.add(new HostPermissionMatcher(m));
            }
            return pmList;
        }
        else if(type.equalsIgnoreCase("ip")) {
            for(String m : matchStr) {
                pmList.add(new IpPermissionMatcher(m));
            }
            return pmList;
        }
        else {
            throw new ConfigException(kv.fileName, kv.lineNo, BayMessage.CFG_INVALID_PERMISSION_DESCRIPTION(kv.value));
        }
    }
}
