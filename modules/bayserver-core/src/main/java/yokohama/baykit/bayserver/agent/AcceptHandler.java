package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.docker.Port;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;

public class AcceptHandler {

    final GrandAgent agent;

    boolean isShutdown;
    int chCount;

    public AcceptHandler(GrandAgent agent) {
        this.agent = agent;
        this.chCount = 0;
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
                ChannelListener lis = p.newChannelListener(agent.agentId, ch);
                agent.nonBlockingHandler.addChannelListener(ch, lis);
                agent.nonBlockingHandler.askToStart(ch);
                agent.nonBlockingHandler.askToRead(ch);
                chCount++;
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

    public void onClosed() {
        chCount--;
    }


    public synchronized void onBusy() {
        BayLog.debug("%s AcceptHandler:onBusy", agent);
        for(ServerSocketChannel ch: BayServer.anchorablePortMap.keySet()) {
            SelectionKey key = ch.keyFor(agent.selector);
            if(key != null)
                key.cancel();
        }
    }

    public synchronized void onFree() {
        BayLog.debug("%s AcceptHandler:onFree isShutdown=%s", agent, isShutdown);
        if(isShutdown)
            return;

        for(ServerSocketChannel ch: BayServer.anchorablePortMap.keySet()) {
            try {
                ch.register(agent.selector, SelectionKey.OP_ACCEPT);
            }
            catch(ClosedChannelException e) {
                BayLog.error(e);
            }
        }
    }

    public void shutdown() {
        isShutdown = true;
    }


}
