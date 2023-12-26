package yokohama.baykit.bayserver.docker.builtin;

import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.util.CharUtil;
import yokohama.baykit.bayserver.util.StringUtil;
import yokohama.baykit.bayserver.common.WriteOnlyShip;

import java.io.IOException;

public class LogShip extends WriteOnlyShip {

    WriteFileTaxi taxi;

    @Override
    public String toString() {
        return agent + " log#" + shipId + "/" + objectId;
    }


    /////////////////////////////////////
    // Implements Reusable
    /////////////////////////////////////

    @Override
    public void reset() {
        taxi = null;
    }

    /////////////////////////////////////
    // Implements DataListener
    /////////////////////////////////////

    @Override
    public void notifyClose() {

    }

    @Override
    public boolean checkTimeout(int durationSec) {
        return false;
    }

    /////////////////////////////////////
    // Custom methods
    /////////////////////////////////////
    public void init(GrandAgent agt, WriteFileTaxi txi) throws IOException{
        super.init(agt);
        this.taxi = txi;
    }

    public synchronized void log(String data) {
        byte[] bytes = StringUtil.toBytes(data + CharUtil.LF);
        taxi.post(bytes, 0, bytes.length);
    }

}
