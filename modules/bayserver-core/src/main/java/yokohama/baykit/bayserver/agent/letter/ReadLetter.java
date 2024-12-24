package yokohama.baykit.bayserver.agent.letter;

import yokohama.baykit.bayserver.agent.multiplexer.RudderState;

import java.net.InetSocketAddress;

public class ReadLetter extends Letter {
    public int nBytes;
    public InetSocketAddress address;

    public ReadLetter(RudderState st, int n, InetSocketAddress adr) {
        super(st);
        this.nBytes = n;
        this.address = adr;
    }
}
