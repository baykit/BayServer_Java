package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.util.CharUtil;
import yokohama.baykit.bayserver.util.StringUtil;

import java.io.IOException;

public class WriteStreamShip extends WriteOnlyShip {

    WriteStreamTaxi taxi;

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
    public void init(GrandAgent agt, WriteStreamTaxi txi) throws IOException{
        super.init(agt);
        this.taxi = txi;
    }

    public synchronized void log(String data) {
        byte[] bytes = StringUtil.toBytes(data + CharUtil.LF);
        taxi.post(bytes, 0, bytes.length);
    }

}
