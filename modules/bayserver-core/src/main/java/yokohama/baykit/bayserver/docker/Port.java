package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.common.InboundShip;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;
import yokohama.baykit.bayserver.rudder.Rudder;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;

public interface Port {

    String protocol();

    String host();

    int port();

    String socketPath();

    SocketAddress address() throws IOException;

    boolean anchored();

    boolean secure();

    int timeoutSec();

    ArrayList<String[]> additionalHeaders();

    Collection<City> cities();

    City findCity(String name);

    void onConnected(int agentId, Rudder rd) throws HttpException;

    void returnProtocolHandler(int agentId, ProtocolHandler protoHnd);

    void returnShip(InboundShip ship);

}
