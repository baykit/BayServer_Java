package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.agent.ChannelListener;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;

/**
 * Managements I/O Multiplexing
 *  (Possible implementations include the select system call, event APIs, or threading)
 */
public interface Multiplexer {

    void addChannelListener(SelectableChannel ch, ChannelListener lis);

    void reqStart(SelectableChannel ch);

    void reqConnect(SocketChannel ch, SocketAddress addr) throws IOException;

    void reqRead(SelectableChannel ch);

    void reqWrite(SelectableChannel ch);

    void reqClose(SelectableChannel ch);

    void runCommandReceiver(Pipe.SourceChannel readCh, Pipe.SinkChannel writeCh);

    void shutdown();
}
