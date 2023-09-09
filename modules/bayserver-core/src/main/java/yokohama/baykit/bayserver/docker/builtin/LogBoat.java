package yokohama.baykit.bayserver.docker.builtin;

import yokohama.baykit.bayserver.util.CharUtil;
import yokohama.baykit.bayserver.util.Reusable;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.watercraft.Boat;

import java.io.FileOutputStream;
import java.io.IOException;

public class LogBoat extends Boat implements Reusable {

    WriteFileTaxi taxi;
    String fileName;

    @Override
    public String toString() {
        return "lboat#" + boatId + "/" + objectId + " file=" + fileName;
    }


    ////////////////////////////////////////////////////////////////////
    // Implements Reusable
    ////////////////////////////////////////////////////////////////////

    @Override
    public void reset() {
        taxi = null;
        fileName = null;
    }

    ////////////////////////////////////////////////////////////////////
    // Implements DataListener
    ////////////////////////////////////////////////////////////////////

    @Override
    public void notifyClose() {

    }


    ////////////////////////////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////////////////////////////
    public void init(String fileName, WriteFileTaxi txi) throws IOException{
        super.init();
        this.taxi = txi;
        this.fileName = fileName;
        taxi.init(new FileOutputStream(fileName), this);
    }

    public synchronized void log(String data) {
        byte[] bytes = StringUtil.toBytes(data + CharUtil.LF);
        taxi.post(bytes, 0, bytes.length);
    }

}
