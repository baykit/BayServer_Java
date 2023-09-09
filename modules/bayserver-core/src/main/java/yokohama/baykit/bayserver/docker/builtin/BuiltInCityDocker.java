package yokohama.baykit.bayserver.docker.builtin;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.docker.*;
import yokohama.baykit.bayserver.docker.base.DockerBase;
import yokohama.baykit.bayserver.docker.file.FileDocker;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.util.URLDecoder;
import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.docker.*;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class BuiltInCityDocker extends DockerBase implements City {

    ArrayList<Town> townList = new ArrayList<>();
    Town defaultTown;

    ArrayList<Club> clubList = new ArrayList<>();
    Club defaultClub;
    
    ArrayList<Log> logList = new ArrayList<>();
    ArrayList<Permission> permissionList = new ArrayList<>();

    Trouble trouble;

    String name;

    private class  ClubMatchInfo {
        public Club club;
        public String scriptName;
        public String pathInfo;
    }

    private class MatchInfo {
        Town town;
        ClubMatchInfo clubMatch;
        String queryString;
        String redirectURI;
        String rewrittenURI;
    }

    @Override
    public String toString() {
        return "City[" + name + "]";
    }


    /////////////////////////////////////////////////////////////////
    // Implements Docker
    /////////////////////////////////////////////////////////////////
    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);

        this.name = elm.arg;
        townList.sort((d1, d2) -> Integer.compare(d2.name().length(), d1.name().length()));

        for(Town t : townList) {
            BayLog.debug(BayMessage.get(Symbol.MSG_SETTING_UP_TOWN, t.name(), t.location()));
        }

        defaultTown = new BuiltInTownDocker();
        defaultClub = new FileDocker();
    }

    /////////////////////////////////////////////////////////////////
    // Implements DockerBase
    /////////////////////////////////////////////////////////////////
    @Override
    public boolean initDocker(Docker dkr) throws ConfigException {
        if (dkr instanceof Town)
            townList.add((Town) dkr);
        else if (dkr instanceof Club)
            clubList.add((Club) dkr);
        else if (dkr instanceof Log)
            logList.add((Log) dkr);
        else if (dkr instanceof Permission)
            permissionList.add((Permission) dkr);
        else if (dkr instanceof Trouble)
            trouble = (Trouble)dkr;
        else
            return false;
        return true;
    }

    /////////////////////////////////////////////////////////////////
    // Implements methods
    /////////////////////////////////////////////////////////////////

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Club> clubs() {
        return clubList;
    }

    @Override
    public List<Town> towns() {
        return townList;
    }

    @Override
    public void enter(Tour tur) throws HttpException {
        BayLog.debug("%s City[%s] Request URI: %s", tur, name, tur.req.uri);

        tur.city = this;

        for (Permission p : permissionList) {
            p.tourAdmitted(tur);
        }

        MatchInfo mInfo = getTownAndClub(tur.req.uri);
        if(mInfo == null) {
            throw new HttpException(HttpStatus.NOT_FOUND, tur.req.uri);
        }

        mInfo.town.checkAdmitted(tur);

        if(mInfo.redirectURI != null) {
            throw HttpException.movedTemp(mInfo.redirectURI);
        }
        else {
            if(BayLog.isDebugMode())
                BayLog.debug("%s Town[%s] Club[%s]", tur, mInfo.town.name(), mInfo.clubMatch.club);
            tur.req.queryString = mInfo.queryString;
            tur.req.scriptName = mInfo.clubMatch.scriptName;

            if(mInfo.clubMatch.club.charset() != null) {
                tur.req.setCharset(mInfo.clubMatch.club.charset());
                tur.res.setCharset(mInfo.clubMatch.club.charset());
            }
            else {
                tur.req.setCharset(BayServer.harbor.charset());
                tur.res.setCharset(BayServer.harbor.charset());
            }

            tur.req.pathInfo = mInfo.clubMatch.pathInfo;
            if(tur.req.pathInfo != null && mInfo.clubMatch.club.decodePathInfo()) {
                try {
                    tur.req.pathInfo = URLDecoder.decode(tur.req.pathInfo, tur.req.charset());
                }
                catch(UnsupportedEncodingException e) {
                    BayLog.error(e);
                    try {
                        tur.req.pathInfo = URLDecoder.decode(tur.req.pathInfo, "UTF-8");
                    }
                    catch (UnsupportedEncodingException ee) {
                        BayLog.error(ee);
                    }
                }
            }
            if(mInfo.rewrittenURI != null) {
                tur.req.rewrittenURI = mInfo.rewrittenURI;  // URI is rewritten
            }

            Club club = mInfo.clubMatch.club;
            tur.town = mInfo.town;
            tur.club = club;
            club.arrive(tur);
        }
    }

    @Override
    public Trouble getTrouble() {
        return trouble;
    }

    @Override
    public void log(Tour tour) {
        for(Log d : logList) {
            try {
                d.log(tour);
            } catch (IOException e) {
                BayLog.error(e);
            }
        }
    }

    /////////////////////////////////////////////////////////////////
    // Private methods
    /////////////////////////////////////////////////////////////////

    private ClubMatchInfo clubMaches(ArrayList<Club> clubList, String relUri, String townName) {

        ClubMatchInfo mi = new ClubMatchInfo();
        Club anyd = null;

        for (Club d : clubList) {
            if (d.fileName().equals("*") && d.extension() == null) {
                // Ignore any match club
                anyd = d;
                break;
            }
        }

        // search for club
        StringTokenizer st = new StringTokenizer(relUri, "/");
        String relScriptName = "";
        loop:
        while (st.hasMoreTokens()) {
            String fname = st.nextToken();
            if(!relScriptName.equals(""))
                relScriptName += '/';
            relScriptName += fname;
            for (Club d : clubList) {
                if(d == anyd) {
                    // Ignore any match club
                    continue;
                }

                if (d.matches(fname)) {
                    mi.club = d;
                    break loop;
                }
            }
        }

        if (mi.club == null && anyd != null) {
            mi.club = anyd;
        }

        if (mi.club == null)
            return null;

        if (townName.equals("/") &&  relScriptName.equals("")) {
            mi.scriptName = "/";
            mi.pathInfo = null;
        }
        else {
            mi.scriptName = townName + relScriptName;
            if (relScriptName.length() == relUri.length())
                mi.pathInfo = null;
            else
                mi.pathInfo = relUri.substring(relScriptName.length());
        }

        return mi;
    }


    /**
     * Determine club from request URI
     */
    private MatchInfo getTownAndClub(String reqUri) {
        if(reqUri == null)
            throw new NullPointerException();
        MatchInfo mi = new MatchInfo();

        String uri = reqUri;
        int pos = uri.indexOf('?');
        if(pos != -1) {
            mi.queryString = uri.substring(pos + 1);
            uri = uri.substring(0, pos);
        }

        for(Town t : townList) {
            Town.MatchType m = t.matches(uri);
            if (m == Town.MatchType.NOT_MATCHED)
                continue;

            // town matched
            mi.town = t;
            if (m == Town.MatchType.CLOSE) {
                mi.redirectURI = uri + "/";
                if(mi.queryString != null)
                    mi.redirectURI += mi.queryString;
                return mi;
            }

            String orgUri = uri;
            uri = t.reroute(uri);
            if(!uri.equals(orgUri))
                mi.rewrittenURI = uri;

            String rel = uri.substring(t.name().length());

            mi.clubMatch = clubMaches(t.clubs(), rel, t.name());

            if(mi.clubMatch == null) {
                mi.clubMatch = clubMaches(clubList, rel, t.name());
            }

            if (mi.clubMatch == null) {
                // check index file
                if(uri.endsWith("/") && !StringUtil.empty(t.welcomeFile())) {
                    String indexUri = uri + t.welcomeFile();
                    String relUri = rel + t.welcomeFile();
                    File indexLocation = new File(t.location(), relUri);
                    if(indexLocation.isFile()) {
                        if (mi.queryString != null)
                            indexUri += "?" + mi.queryString;
                        MatchInfo m2 = getTownAndClub(indexUri);
                        if (m2 != null) {
                            // matched
                            m2.rewrittenURI = indexUri;
                            return m2;
                        }
                    }
                }

                // default club matches
                mi.clubMatch = new ClubMatchInfo();
                mi.clubMatch.club = defaultClub;
                mi.clubMatch.scriptName = null;
                mi.clubMatch.pathInfo = null;
            }

            return mi;
        }

        return null;
    }


}
