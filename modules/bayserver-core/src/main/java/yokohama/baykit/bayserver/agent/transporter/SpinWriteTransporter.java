package yokohama.baykit.bayserver.agent.transporter;

import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.agent.SpinHandler;
import yokohama.baykit.bayserver.util.Reusable;
import yokohama.baykit.bayserver.common.Valve;

import java.io.File;
import java.io.IOException;


/**
 * SpinWriteTransporter is not supported because there are no API for asynchronous writeing
 */
public class SpinWriteTransporter implements SpinHandler.SpinListener, Reusable, Valve {

    public SpinWriteTransporter(int bufsize) {
    }

    public void init(GrandAgent agt, File file) throws IOException {
    }


    ////////////////////////////////////////////////////////////////////
    // implements Reusable
    ////////////////////////////////////////////////////////////////////

    public void reset() {
    }


    ////////////////////////////////////////////////////////////////////
    // implements SpinListener
    ////////////////////////////////////////////////////////////////////

    @Override
    public NextSocketAction lap(boolean spun[]) {
        spun[0] = true;
        return null;
    }

    @Override
    public boolean checkTimeout(int durationSec) {
        return false;
    }

    @Override
    public void close() {
    }

    ////////////////////////////////////////////////////////////////////
    // Implements Valve
    ////////////////////////////////////////////////////////////////////

    @Override
    public void openValve() {
    }

    ////////////////////////////////////////////////////////////////////
    // Custom methods
    ////////////////////////////////////////////////////////////////////

}
