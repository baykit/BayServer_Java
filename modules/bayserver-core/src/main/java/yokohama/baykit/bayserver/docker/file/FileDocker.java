package yokohama.baykit.bayserver.docker.file;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.ConfigException;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.base.ClubBase;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.util.URLDecoder;

import java.io.File;

public class FileDocker extends ClubBase {

    boolean listFiles = false;

    private FileStore fileStore;

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

        // Setup FileStore
        if(BayServer.harbor.enableCache() && fileStore == null) {
            fileStore = new FileStore(BayServer.harbor.cacheLifespanSec(), BayServer.harbor.cacheSizeMb() * 1024 * 1024);
        }

        String relPath = tur.req.rewrittenURI != null ? tur.req.rewrittenURI : tur.req.uri;
        if(!StringUtil.empty(tur.town.name()))
            relPath = relPath.substring(tur.town.name().length());
        int pos = relPath.indexOf('?');
        if(pos != -1)
            relPath = relPath.substring(0, pos);

        try {
            relPath = URLDecoder.decode(relPath, tur.req.charset());
        }
        catch(Exception e) {
            BayLog.error("Cannot decode path: %s: %s", relPath, e);
        }

        File real = new File(tur.town.location(), relPath);

        FileStore.FileContentStatus status = null;
        boolean[] reading = new boolean[1];

        if(fileStore != null) {
            status = fileStore.get(real, reading);
        }
        else {
            if (real.isDirectory())
                status = null;
            else
                status = new FileStore.FileContentStatus(null, FileStore.FileContentStatus.EXCEEDED);
        }

        if(status == null) {
            handleDirectory(tur, real);
        }
        else {
            FileContentHandler handler = new FileContentHandler(tur, real, status, tur.res.charset());
            tur.req.setReqContentHandler(handler);
        }
    }


    public void handleDirectory(Tour tur, File path) throws HttpException {
        if(listFiles) {
            DirectoryTrain train = new DirectoryTrain(tur, path);
            train.startTour();
        }
        else {
            throw new HttpException(HttpStatus.FORBIDDEN, "Directory scan is prohibited");
        }


    }
}
