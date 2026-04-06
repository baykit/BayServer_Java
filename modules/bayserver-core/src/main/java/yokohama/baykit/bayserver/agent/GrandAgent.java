package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.letter.*;
import yokohama.baykit.bayserver.agent.multiplexer.*;
import yokohama.baykit.bayserver.common.*;
import yokohama.baykit.bayserver.docker.Harbor;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.docker.base.PortBase;
import yokohama.baykit.bayserver.rudder.*;
import yokohama.baykit.bayserver.util.Pair;
import yokohama.baykit.bayserver.util.RoughTime;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.List;

public class GrandAgent extends Thread {

    protected final ArrayList<Letter> letterQueue = new ArrayList<>();

    public static final int CMD_OK = 0;
    public static final int CMD_CLOSE = 1;
    public static final int CMD_RELOAD_CERT = 2;
    public static final int CMD_MEM_USAGE = 3;
    public static final int CMD_SHUTDOWN = 4;
    public static final int CMD_ABORT = 5;
    public static final int CMD_CATCHUP = 6;

    public static final int SELECT_TIMEOUT_SEC = 10;

    static int agentCount;
    static int maxShips;
    static int maxAgentId;
    private static final ArrayList<GrandAgent> agents = new ArrayList<>();
    public static List<LifecycleListener> listeners = new ArrayList<>();

    public int selectTimeoutSec = SELECT_TIMEOUT_SEC;
    public final int agentId;
    public Multiplexer netMultiplexer;
    public Multiplexer fileMultiplexer;
    public Multiplexer jobMultiplexer;
    public Multiplexer taxiMultiplexer;
    public SpinMultiplexer spinMultiplexer;
    public Multiplexer spiderMultiplexer;
    public Multiplexer pigeonMultiplexer;
    public Recipient recipient;

    public final int maxInboundShips;
    public boolean aborted;
    private boolean anchorable;
    private ArrayList<TimerHandler> timerHandlers = new ArrayList<>();
    public CommandReceiver commandReceiver;
    private ArrayList<Runnable> postponeQueue = new ArrayList<>();
    private long lastTimeoutCheck;
    private boolean busy;

    private ArrayList<Rudder> anchorableRudders = new ArrayList<>();
    private ArrayList<Rudder> unanchorableRudders = new ArrayList<>();

    public GrandAgent(
            int agentId,
            int maxShips,
            boolean anchorable) {
        this.agentId = agentId;

        this.maxInboundShips = maxShips > 0 ? maxShips : 1;
        this.spiderMultiplexer = new SpiderMultiplexer(this, anchorable);
        this.jobMultiplexer = new JobMultiplexer(this, anchorable);
        this.taxiMultiplexer = new TaxiMultiplexer(this);
        this.spinMultiplexer = new SpinMultiplexer(this);
        this.pigeonMultiplexer = new PigeonMultiplexer(this, anchorable);
        this.anchorable = anchorable;

        switch(BayServer.harbor.recipient()) {
            case Spider:
                this.recipient = (Recipient)this.spiderMultiplexer;
                break;

            case Pipe:
                this.recipient = new PipeRecipient();
                break;
        }

        switch(BayServer.harbor.netMultiplexer()) {
            case Spider:
                this.netMultiplexer = this.spiderMultiplexer;
                break;

            case Job:
                this.netMultiplexer = this.jobMultiplexer;
                break;

            case Pigeon:
                this.netMultiplexer = this.pigeonMultiplexer;
                break;

            case Spin:
            case Taxi:
            case Train:
                throw new Sink("Multiplexer not supported: %s", Harbor.getMultiplexerTypeName(BayServer.harbor.netMultiplexer()));
        }

        switch (BayServer.harbor.fileMultiplexer()) {
            case Spin: {
                this.fileMultiplexer = spinMultiplexer;
                break;
            }

            case Job: {
                this.fileMultiplexer = jobMultiplexer;
                break;
            }

            case Taxi: {
                this.fileMultiplexer = taxiMultiplexer;
                break;
            }

            case Pigeon: {
                this.fileMultiplexer = pigeonMultiplexer;
                break;
            }

            default:
                throw new Sink("Multiplexer not supported: %s", Harbor.getMultiplexerTypeName(BayServer.harbor.fileMultiplexer()));
        }
    }

    public String toString() {
        return "agt#" + agentId + "(" + Thread.currentThread().getName() + ")";
    }

    ////////////////////////////////////////////
    // Implements Thread                      //
    ////////////////////////////////////////////

    @Override
    public void run() {
        BayLog.info(BayMessage.get(Symbol.MSG_RUNNING_GRAND_AGENT, this));
        try {
            // Adds read channel of command receiver
            if(netMultiplexer.isNonBlocking())
                commandReceiver.rudder.setNonBlocking();

            netMultiplexer.reqRead(commandReceiver.rudder);

            if(anchorable) {
                // Adds server socket channel of anchorable ports
                for(Pair<Channel, Port> pair: BayServer.anchorablePorts) {
                    Rudder rd;
                    if(pair.a instanceof ServerSocketChannel) {
                        rd = new ServerSocketChannelRudder((ServerSocketChannel)pair.a);
                    }
                    else {
                        rd = new AsynchronousServerSocketChannelRudder((AsynchronousServerSocketChannel)pair.a);
                    }
                    anchorableRudders.add(rd);

                    if(netMultiplexer.isNonBlocking()) {
                        try {
                            rd.setNonBlocking();
                        }
                        catch(IOException e) {
                            BayLog.fatal(e);
                        }
                    }
                    RudderState st = RudderStateStore.getStore(agentId).rent();
                    st.init(rd);
                    netMultiplexer.addRudderState(rd, st);
                }
            }
            else {
                // Adds server socket  up unanchorable ports
                for(Pair<Channel, Port> pair: BayServer.unanchorablePorts) {
                    Rudder rd = new DatagramChannelRudder((DatagramChannel)pair.a);
                    unanchorableRudders.add(rd);

                    if(netMultiplexer.isNonBlocking()) {
                        try {
                            rd.setNonBlocking();
                        }
                        catch(IOException e) {
                            BayLog.fatal(e);
                        }
                    }
                    pair.b.onConnected(agentId, rd);
                }
            }

            netMultiplexer.onFree();
            while (true) {
                boolean received;
                if (!spinMultiplexer.isEmpty()) {
                    // If "SpinHandler" is running, the select function does not block.
                    received = recipient.receive(false);
                    spinMultiplexer.processData();
                }
                else {
                    received = recipient.receive(true);
                }

                //BayLog.debug("selected: %d", count);
                if(aborted) {
                    BayLog.info("%s aborted by another thread", this);
                    break;
                }

                if(spinMultiplexer.isEmpty() && letterQueue.isEmpty()) {
                    // timed out
                    // check per 10 seconds
                    if((RoughTime.currentTimeMillis() - lastTimeoutCheck) / 1000 >= 10)
                        ring();
                }

                //spinMultiplexer.processData();
                while(!letterQueue.isEmpty()) {
                    Letter let;
                    synchronized (letterQueue) {
                        let = letterQueue.remove(0);
                    }

                    checkRudderState(let.rudder);
                    RudderState st = let.multiplexer.getRudderState(let.rudder);
                    if(st == null) {
                        BayLog.debug("%s rudder is already returned: %s", this, let.rudder);
                        continue;
                    }

                    if(let instanceof AcceptedLetter) {
                        onAccepted((AcceptedLetter) let, st);
                    }
                    else if(let instanceof ConnectedLetter) {
                        onConnected((ConnectedLetter) let, st);
                    }
                    else if(let instanceof ReadLetter) {
                        onRead((ReadLetter) let, st);
                    }
                    else if(let instanceof WroteLetter) {
                        onWrote((WroteLetter) let, st);
                    }
                    else if(let instanceof ClosedLetter) {
                        onClosed((ClosedLetter) let, st);
                    }
                    else if(let instanceof ErrorLetter) {
                        onError((ErrorLetter) let, st);
                    }
                }
            }
        }
        catch (Throwable e) {
            // If error occurs, grand agent ends
            BayLog.fatal(e, "%s Fatal error!", this);
            if(e instanceof OutOfMemoryError) {
                System.exit(1);
            }
        }
        finally {
            BayLog.info("%s end", this);
            shutdown();
        }

    }


    ////////////////////////////////////////////
    // Custom methods                         //
    ////////////////////////////////////////////

    public void addTimerHandler(TimerHandler th) {
        timerHandlers.add(th);
    }

    public void removeTimerHandler(TimerHandler th) {
        timerHandlers.remove(th);
    }

    // The timer goes off
    private void ring() {
        for(TimerHandler th: timerHandlers) {
            th.onTimer();
        }
        lastTimeoutCheck = RoughTime.currentTimeMillis();
    }

    public void addCommandReceiver(Rudder rd) {
        commandReceiver = new CommandReceiver();
        Transporter comTransporter = new PlainTransporter(netMultiplexer, commandReceiver, true, 8, false);
        commandReceiver.init(agentId, rd, comTransporter);
        RudderState st = RudderStateStore.getStore(agentId).rent();
        st.init(commandReceiver.rudder, comTransporter);
        netMultiplexer.addRudderState(commandReceiver.rudder, st);
    }

    public void sendAcceptedLetter(Rudder rd, Multiplexer mpx, Rudder clientRd, boolean wakeup) {
        if(rd == null)
            throw new NullPointerException();
        sendLetter(new AcceptedLetter(rd, mpx, clientRd), wakeup);
    }

    public void sendConnectedLetter(Rudder rd, Multiplexer mpx, boolean wakeup) {
        if(rd == null)
            throw new NullPointerException();
        sendLetter(new ConnectedLetter(rd, mpx), wakeup);
    }

    public void sendReadLetter(Rudder rd, Multiplexer mpx, int n, InetSocketAddress adr, boolean wakeup) {
        if(rd == null)
            throw new NullPointerException();
        sendLetter(new ReadLetter(rd, mpx, n, adr), wakeup);
    }

    public void sendWroteLetter(Rudder rd, Multiplexer mpx, int n, boolean wakeup) {
        if(rd == null)
            throw new NullPointerException();
        sendLetter(new WroteLetter(rd, mpx, n), wakeup);
    }

    public void sendClosedLetter(Rudder rd, Multiplexer mpx, boolean wakeup) {
        if(rd == null)
            throw new NullPointerException();
        sendLetter(new ClosedLetter(rd, mpx), wakeup);
    }

    public void sendErrorLetter(Rudder rd, Multiplexer mpx, Throwable e, boolean wakeup) {
        if(rd == null)
            throw new NullPointerException();
        sendLetter(new ErrorLetter(rd, mpx, e), wakeup);
    }

    public void shutdown() {
        BayLog.debug("%s shutdown aborted=%b", this, aborted);
        if(aborted)
            return;

        aborted = true;

        BayLog.debug("%s shutdown netMultiplexer", this);
        netMultiplexer.shutdown();

        BayLog.debug("%s remove listeners", this);
        listeners.forEach(lis -> lis.remove(agentId));
        commandReceiver.end();
        agents.set(agentId-1, null);
    }

    void abort() {
        BayLog.fatal("%s abort", this);
    }

    void reloadCert() {
        for(Pair<Channel, Port> pair : BayServer.anchorablePorts) {
            if(pair.b.secure()) {
                PortBase pbase = (PortBase)pair.b;
                try {
                    pbase.secureDocker.reloadCert();
                } catch (Exception e) {
                    BayLog.error(e);
                }
            }
        }
    }

    public synchronized void addPostpone(Runnable p) {
        postponeQueue.add(p);
    }

    private int countPostpone() {
        return postponeQueue.size();
    }

    public void reqCatchUp() {
        BayLog.debug("%s Req catchUp", this);
        if(countPostpone() > 0) {
            catchUp();
        }
        else {
            try {
                commandReceiver.sendCommandToMonitor(this, CMD_CATCHUP, false);
            }
            catch (IOException e) {
                BayLog.error(e);
                abort();
            }
        }
    }

    synchronized void catchUp() {
        BayLog.debug("%s catchUp", this);
        if(!postponeQueue.isEmpty()) {
            Runnable r = postponeQueue.remove(0);
            r.run();
        }
    }

    public ArrayList<Rudder> anchorableRudders() {
        return anchorableRudders;
    }

    public ArrayList<Rudder> unanchorableRudders() {
        return unanchorableRudders;
    }

    ////////////////////////////////////////////
    // Private methods                        //
    ////////////////////////////////////////////

    protected void sendLetter(Letter let, boolean wakeup) {
        synchronized (letterQueue) {
            letterQueue.add(let);
        }

        if(wakeup)
            recipient.wakeup();
    }

    private void onAccepted(AcceptedLetter let, RudderState st) {
        BayLog.debug("%s onAccepted rd=%s", this, st.rudder);

        try {
            Port p = BayServer.findAnchorablePort(ChannelRudder.getChannel(st.rudder));
            p.onConnected(agentId, let.clientRudder);
        }
        catch (HttpException e) {
            st.transporter.onError(st.rudder, e);
            nextAction(st, NextSocketAction.Close, false);
        }

        if (netMultiplexer.isBusy()) {
            BayLog.warn("%s net multiplexer is busy: %s", this, netMultiplexer);
            netMultiplexer.onBusy();
            busy = true;
        }
        else {
            st.multiplexer.nextAccept(st);
        }
    }

    private void onConnected(ConnectedLetter let, RudderState st) {
        BayLog.debug("%s connected rd=%s", this, st.rudder);
        NextSocketAction nextAct;
        try {
            nextAct = st.transporter.onConnect(st.rudder);
            BayLog.debug("%s nextAct=%s", this, nextAct);
        }
        catch (IOException e) {
            st.transporter.onError(st.rudder, e);
            nextAct = NextSocketAction.Close;
        }

        if(nextAct == NextSocketAction.Read) {
            // Read more
            st.multiplexer.cancelWrite(st);
        }

        nextAction(st, nextAct, false);
    }

    private void onRead(ReadLetter let, RudderState st) {
        NextSocketAction nextAct;

        try {
            BayLog.debug("%s read %d bytes (rd=%s) st=%d buf=%s", this, let.nBytes, st.rudder, st.hashCode(), st.readBuf);
            st.bytesRead += let.nBytes;

            if (let.nBytes <= 0) {
                st.readBuf.limit(0);
                nextAct = st.transporter.onRead(st.rudder, st.readBuf, let.address);
            }
            else {
                nextAct = st.transporter.onRead(st.rudder, st.readBuf, let.address);
                //BayLog.debug("%s return read before buf=%s", this, st.readBuf);
                st.readBuf.compact();
                //BayLog.debug("%s return read buf=%s", this, st.readBuf);
            }

        }
        catch (IOException e) {
            st.transporter.onError(st.rudder, e);
            nextAct = NextSocketAction.Close;
        }

        nextAction(st, nextAct, true);
    }

    private void onWrote(WroteLetter let, RudderState st) {
        BayLog.debug("%s wrote %d bytes rd=%s qlen=%d", this, let.nBytes, st.rudder, st.writeQueue.size());
        st.bytesWrote += let.nBytes;

        if(st.writeQueue.isEmpty()) {
            BayLog.debug("%s Write queue is empty: rd=%s", this, st.rudder);
            return;
        }

        boolean writeMore;
        while(true) {
            WriteUnit unit = st.writeQueue.get(0);
            //BayLog.debug("%s wrote buf=%s", this, unit.buf);
            if (unit.hasRemaining()) {
                BayLog.debug("Could not write enough data buf=%s", unit.buf);
                writeMore = true;
                break;
            }
            else {
                // Removes write unit from writeQueue
                st.multiplexer.consumeOldestUnit(st);

                if (st.writeQueue.isEmpty()) {
                    writeMore = false;
                    st.writing[0] = false;
                    break;
                }
            }
        }

        if (writeMore) {
            st.multiplexer.nextWrite(st);
        }
        else {
            if (st.finale) {
                // Close
                BayLog.debug("%s finale return Close", this);
                nextAction(st, NextSocketAction.Close, false);
            }
            else {
                // Write off
                st.multiplexer.cancelWrite(st);
            }
        }
    }

    private void onClosed(ClosedLetter let, RudderState st) {
        BayLog.debug("%s on Closed rd=%s", this, st.rudder);
        st.multiplexer.removeRudderState(st.rudder);

        while(st.multiplexer.consumeOldestUnit(st)) {
        }

        if (st.transporter != null)
            st.transporter.onClosed(st.rudder);

        RudderStateStore.getStore(agentId).Return(st);

        if(busy && !netMultiplexer.isBusy()) {
            BayLog.warn("%s net multiplexer is free: %s", this, netMultiplexer);
            netMultiplexer.onFree();
            busy = false;
        }
    }

    private void onError(ErrorLetter let, RudderState st) throws Throwable {

        try {
            throw let.err;
        }
        catch (IOException | HttpException e) {
            if(st.transporter != null) {
                st.transporter.onError(st.rudder, e);
            }
            else {
                BayLog.error(e, "%s onError error=%s", this, e);
            }
            nextAction(st, NextSocketAction.Close, false);
        }

    }

    private void nextAction(RudderState st, NextSocketAction act, boolean reading) {
        BayLog.debug("%s next action: %s (reading=%b)", this, act, reading);
        boolean cancel = false;

        switch(act) {
            case Continue:
                if(reading)
                    st.multiplexer.nextRead(st);
                break;

            case Read:
                st.multiplexer.nextRead(st);
                break;

            case Write:
                //if(reading)
                //    cancel = true;
                break;

            case Close:
                //if(reading)
                //    cancel = true;
                st.multiplexer.reqClose(st.rudder);
                break;

            case Suspend:
                if(reading)
                    cancel = true;
                break;

            default:
                throw new IllegalArgumentException("NextAction=" + act);
        }

        if(cancel) {
            st.multiplexer.cancelRead(st);
            synchronized (st.reading) {
                BayLog.debug("%s Reading off %s", this, st.rudder);
                st.reading[0] = false;
            }
        }

        st.access();
    }

    /////////////////////////////////////////////////////////////////////////////
    // static methods                                                          //
    /////////////////////////////////////////////////////////////////////////////
    public static void init(
            int agentIds[],
            int maxShips) throws IOException {
        GrandAgent.agentCount = agentIds.length;
        GrandAgent.maxShips = maxShips;
    }

    public static GrandAgent get(int id) {
        return agents.get(id-1);
    }

    public static GrandAgent add(int agtId, boolean anchorable) {
        if(agtId == -1)
            agtId = ++maxAgentId;
        BayLog.debug("Add agent: id=%d anchorable=%s", agtId, anchorable);

        if(agtId > maxAgentId)
            maxAgentId = agtId;

        GrandAgent agt = new GrandAgent(agtId, maxShips, anchorable);
        while(agents.size() < agtId) {
            agents.add(null);
        }
        agents.set(agtId-1, agt);

        listeners.forEach(lis -> lis.add(agt.agentId));
        return agt;
    }

    public static void addLifecycleListener(LifecycleListener lis) {
        listeners.add(lis);
    }


    /////////////////////////////////////////////////////////////////////////////
    // private methods                                                         //
    /////////////////////////////////////////////////////////////////////////////

    private void checkRudderState(Rudder rd) {
        if(rd instanceof ChannelRudder) {
            RudderState st = (RudderState) ((ChannelRudder)rd).state;
            if(st != null && st.rudder != rd) {
                throw new Sink("%s Invalid rd=%s st=%s", this, rd, st);
            }
        }
    }
}
