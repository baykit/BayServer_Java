package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.common.Valve;

import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;

public class NonBlockingValve implements Valve {

    private NonBlockingHandler handler;
    private Channel channel;

    public NonBlockingValve(NonBlockingHandler hnd, Channel ch) {
        this.handler = hnd;
        this.channel = ch;
    }

    /////////////////////////////////////////
    // Implements Valve
    /////////////////////////////////////////
    @Override
    public void openReadValve() {
        handler.askToRead((SelectableChannel) channel);
    }

    @Override
    public void openWriteValve() {
        handler.askToWrite((SelectableChannel) channel);
    }

    @Override
    public void destroy() {
        handler.askToClose((SelectableChannel) channel);
    }
}
