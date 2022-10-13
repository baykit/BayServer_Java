package baykit.bayserver.docker.file;

import baykit.bayserver.*;
import baykit.bayserver.bcf.BcfKeyVal;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.bcf.BcfElement;
import baykit.bayserver.docker.Docker;
import baykit.bayserver.docker.base.ClubBase;
import baykit.bayserver.util.StringUtil;
import baykit.bayserver.util.URLDecoder;

import java.io.File;
import java.io.UnsupportedEncodingException;

public class FileDocker extends ClubBase {

    boolean listFiles = false;

    ///////////////////////////////////////////////////////////////////////
    // Implements Docker
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);
    }

    ///////////////////////////////////////////////////////////////////////
    // Implements DockerBase
    ///////////////////////////////////////////////////////////////////////

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch (kv.key.toLowerCase()) {
            default:
                return super.initKeyVal(kv);

            case "listfiles":
                listFiles = StringUtil.parseBool(kv.value);
                break;
        }
        return true;
    }

    ///////////////////////////////////////////////////////////////////////
    // Implements Club
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void arrive(Tour tur) throws HttpException {

        String relPath = tur.req.rewrittenURI != null ? tur.req.rewrittenURI : tur.req.uri;
        if(!StringUtil.empty(tur.town.name()))
            relPath = relPath.substring(tur.town.name().length());
        int pos = relPath.indexOf('?');
        if(pos != -1)
            relPath = relPath.substring(0, pos);

        try {
            relPath = URLDecoder.decode(relPath, tur.req.charset());
        }
        catch(UnsupportedEncodingException e) {
            BayLog.error("Cannot decode path: %s: %s", relPath, e);
        }

        File real = new File(tur.town.location(), relPath);

        if(real.isDirectory() && listFiles) {
            DirectoryTrain train = new DirectoryTrain(tur, real);
            train.startTour();
        }
        else {
            FileContentHandler handler = new FileContentHandler(real);
            tur.req.setContentHandler(handler);
        }
    }
}
