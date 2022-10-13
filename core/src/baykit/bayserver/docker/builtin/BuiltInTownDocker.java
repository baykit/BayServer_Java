package baykit.bayserver.docker.builtin;

import baykit.bayserver.*;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.bcf.BcfElement;
import baykit.bayserver.bcf.BcfKeyVal;
import baykit.bayserver.docker.*;
import baykit.bayserver.docker.base.DockerBase;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public final class BuiltInTownDocker extends DockerBase implements Town {
    
    String location;
    String welcome;
    ArrayList<Club> clubList = new ArrayList<>();
    ArrayList<Permission> permissionList = new ArrayList<>();
    ArrayList<Reroute> rerouteList = new ArrayList<>();
    City city;
    String name;

    /////////////////////////////////////////////////////////////////////
    // Implements Docker
    /////////////////////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        String arg = elm.arg;
        if(!arg.startsWith("/"))
            arg = "/" + arg;
        name = arg;
        if(!name.endsWith("/"))
            name = name + "/";
        city = (City)parent;

        super.init(elm, parent);
    }

    /////////////////////////////////////////////////////////////////////
    // Implements DockerBase
    /////////////////////////////////////////////////////////////////////

    @Override
    public boolean initDocker(Docker dkr) throws ConfigException {
        if (dkr instanceof Club) {
            clubList.add((Club) dkr);
        } else if (dkr instanceof Permission) {
            permissionList.add((Permission) dkr);
        } else if (dkr instanceof Reroute) {
            rerouteList.add((Reroute) dkr);
        }
        else {
            return super.initDocker(dkr);
        }
        return true;
    }

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch(kv.key.toLowerCase()) {
            default:
                return false;

            case "location": {
                location = kv.value;
                File loc = new File(location);
                if(!loc.isAbsolute())
                    loc = new File(BayServer.getLocation(location));
                if(!loc.isDirectory())
                    throw new ConfigException(kv.fileName,  kv.lineNo, BayMessage.CFG_INVALID_LOCATION(location));
                try {
                    location = loc.getCanonicalPath();
                }
                catch(IOException e) {
                    throw new ConfigException(kv.fileName,  kv.lineNo, BayMessage.CFG_INVALID_LOCATION(location), e);
                }
                break;
            }

            case "index":
            case "welcome":
                welcome = kv.value;
                break;
        }
        return true;
    }

    /////////////////////////////////////////////////////////////////////
    // Implements Town
    /////////////////////////////////////////////////////////////////////
    @Override
    public String name() {
        return name;
    }

    @Override
    public City city() {
        return null;
    }

    @Override
    public String location() {
        return location;
    }

    @Override
    public String welcomeFile() {
        return welcome;
    }

    @Override
    public ArrayList<Club> clubs() {
        return clubList;
    }

    @Override
    public String reroute(String uri) {
        for (Reroute r: rerouteList) {
            uri = r.reroute(this, uri);
        }

        return uri;
    }

    @Override
    public MatchType matches(String uri) {
        if(uri.startsWith(name))
            return MatchType.MATCHED;
        else if((uri + "/").equals(name))
            return MatchType.CLOSE;
        else
            return MatchType.NOT_MATCHED;
    }

    @Override
    public void checkAdmitted(Tour tour) throws HttpException {
        for(Permission p : permissionList) {
            p.tourAdmitted(tour);
        }
    }
}
