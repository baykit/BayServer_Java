package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.common.Valve;

import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;

public class MultiplexingValve implements Valve {

    private Multiplexer handler;
    private Channel channel;

    public MultiplexingValve(Multiplexer hnd, Channel ch) {
        this.handler = hnd;
        this.channel = ch;
    }

    /////////////////////////////////////////
    // Implements Valve
    /////////////////////////////////////////
    @Override
    public void openReadValve() {
        handler.reqRead((SelectableChannel) channel);
    }

    @Override
    public void openWriteValve() {
        handler.reqWrite((SelectableChannel) channel);
    }

    @Override
    public void destroy() {
        handler.reqClose((SelectableChannel) channel);
    }
}
