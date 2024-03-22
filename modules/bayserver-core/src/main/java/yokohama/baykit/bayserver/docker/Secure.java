package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.agent.multiplexer.Transporter;
import yokohama.baykit.bayserver.ship.Ship;

public interface Secure {

    void setAppProtocols(String[] protocols);

    void reloadCert() throws Exception;

    Transporter newTransporter(int agtId, Ship ship);
}
