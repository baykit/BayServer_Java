package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.rudder.ChannelRudder;
import yokohama.baykit.bayserver.rudder.NetworkChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.rudder.SocketChannelRudder;

import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

public class AcceptHandler {

    final GrandAgent agent;
    Map<Channel, Rudder> anchorableRudderMap = new HashMap<>();
    public AcceptHandler(GrandAgent agent) {
        this.agent = agent;

        for(NetworkChannelRudder rd: BayServer.anchorablePortMap.keySet()) {
            try {
                rd.setNonBlocking();
            }
            catch(IOException e) {
                BayLog.error(e);
            }
            anchorableRudderMap.put(ChannelRudder.getChannel(rd), rd);
        }
    }


    public void onAcceptable(SelectionKey key) {
        // get channel from server socket
        ServerSocketChannel sch = (ServerSocketChannel) key.channel();
        Rudder serverRd = anchorableRudderMap.get(key.channel());
        Port p = BayServer.anchorablePortMap.get(serverRd);

        //BayLog.debug(this + " onAcceptable");
        SocketChannel ch = null;
        while(true) {
            try {
                ch = sch.accept();
                if (ch == null) {
                    // Another agent caught client socket
                    return;
                }

                SocketChannelRudder rd = new SocketChannelRudder(ch);
                rd.setNonBlocking();
                p.onConnected(agent.agentId, rd);

            } catch (IOException | HttpException e) {
                BayLog.error(e);
                if (ch != null) {
                    try {
                        ch.close();
                    } catch (IOException ee) {

                    }
                }
            }
        }
    }
}
