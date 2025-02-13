package yokohama.baykit.bayserver.agent.letter;

import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.rudder.Rudder;

import java.net.InetSocketAddress;

public class ReadLetter extends Letter {
    public int nBytes;
    public InetSocketAddress address;

    public ReadLetter(int stateId, Rudder rd, Multiplexer mpx, int n, InetSocketAddress adr) {
        super(stateId, rd, mpx);
        this.nBytes = n;
        this.address = adr;
    }
}
