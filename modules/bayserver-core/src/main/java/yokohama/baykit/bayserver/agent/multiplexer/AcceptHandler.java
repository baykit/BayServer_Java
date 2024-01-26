package yokohama.baykit.bayserver.agent.multiplexer;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.common.DataListener;
import yokohama.baykit.bayserver.common.ChannelRudder;
import yokohama.baykit.bayserver.common.Rudder;
import yokohama.baykit.bayserver.docker.Port;

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

        for(Rudder rd: BayServer.anchorablePortMap.keySet()) {
            try {
                ((SocketChannel)ChannelRudder.getChannel(rd)).configureBlocking(false);
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
                try {
                    p.checkAdmitted(ch);
                } catch (HttpException e) {
                    BayLog.error(e);
                    try {
                        ch.close();
                    } catch (IOException ex) {
                    }
                    return;
                }
                //BayServer.debug(ch + " accepted");
                ch.configureBlocking(false);
                Rudder rd = new ChannelRudder(ch);

                DataListener lis = p.newDataListener(agent.agentId, rd);
                Transporter tp = p.newTransporter(agent.agentId, rd);
                RudderState st = new RudderState(rd, lis, tp);
                agent.netMultiplexer.addState(rd, st);
                agent.netMultiplexer.reqRead(rd);
            } catch (IOException e) {
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
