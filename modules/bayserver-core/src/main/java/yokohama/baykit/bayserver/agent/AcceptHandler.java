package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.agent.transporter.DataListener;
import yokohama.baykit.bayserver.agent.transporter.SelectHandler;
import yokohama.baykit.bayserver.common.ChannelRudder;
import yokohama.baykit.bayserver.common.Rudder;
import yokohama.baykit.bayserver.docker.Port;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class AcceptHandler {

    final GrandAgent agent;

    public AcceptHandler(GrandAgent agent) {
        this.agent = agent;
    }


    public void onAcceptable(SelectionKey key) {
        // get channel from server socket
        ServerSocketChannel sch = (ServerSocketChannel) key.channel();
        Port p = BayServer.anchorablePortMap.get(sch);

        //BayLog.debug(this + " onAcceptable");
        SocketChannel ch = null;
        while(true) {
            try {
                // create new listener
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
                SelectHandler h = p.newSelectHandler(agent.agentId, rd);
                RudderState st = new RudderState(rd, lis, h);
                agent.multiplexer.addState(rd, st);
                agent.multiplexer.reqStart(rd);
                agent.multiplexer.reqRead(rd);
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
