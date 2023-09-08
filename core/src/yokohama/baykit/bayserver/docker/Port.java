package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.transporter.Transporter;
import yokohama.baykit.bayserver.docker.base.InboundShip;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
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

    void checkAdmitted(SocketChannel ch) throws HttpException;

    ArrayList<String[]> additionalHeaders();

    Collection<City> cities();

    City findCity(String name);

    Transporter newTransporter(GrandAgent agent, SelectableChannel ch) throws IOException;

    void returnProtocolHandler(GrandAgent agent, ProtocolHandler protoHnd);

    void returnShip(InboundShip ship);

}
