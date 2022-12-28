package baykit.bayserver.docker.wordpress;

import baykit.bayserver.ConfigException;
import baykit.bayserver.bcf.BcfElement;
import baykit.bayserver.docker.Docker;
import baykit.bayserver.docker.Town;
import baykit.bayserver.docker.base.RerouteBase;
import baykit.bayserver.util.StringUtil;

import java.io.File;
import java.util.StringTokenizer;

public class WordPressDocker extends RerouteBase {

    String townPath;

    //////////////////////////////////////////////////////
    // Implements Docker
    //////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);

        Town twn = (Town) parent;
        townPath = twn.location();
    }

    //////////////////////////////////////////////////////
    // Implements Reroute
    //////////////////////////////////////////////////////

    @Override
    public String reroute(Town twn, String uri) {
        StringTokenizer st = new StringTokenizer(uri, "?");
        String uri2 = st.nextToken();
        if(!match(uri2))
            return uri;

        String relPath = uri2.substring(twn.name().length());
        if(relPath.startsWith("/"))
            relPath = relPath.substring(1);

        st = new StringTokenizer(relPath, "/");
        String checkPath = "";
        while(st.hasMoreTokens()) {
            if(StringUtil.isSet(checkPath))
                checkPath += "/";
            checkPath += st.nextToken();
            if(new File(twn.location(), checkPath).exists())
                return uri;
        }

        File f = new File(twn.location(), relPath);
        if(!f.exists())
            return twn.name() + "index.php/" + uri.substring(twn.name().length());
        else
            return uri;
    }
}
