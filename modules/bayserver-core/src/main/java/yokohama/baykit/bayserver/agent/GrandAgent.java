package yokohama.baykit.bayserver.agent;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.agent.multiplexer.*;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.common.Recipient;
import yokohama.baykit.bayserver.docker.Harbor;
import yokohama.baykit.bayserver.docker.Port;
import yokohama.baykit.bayserver.docker.base.PortBase;
import yokohama.baykit.bayserver.rudder.NetworkChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GrandAgent extends Thread {

    private enum LetterType {
        Accepted,
        Connected,
        Read,
        Wrote,
        CloseReq,
    }

    private static class Letter {
        LetterType type;
        RudderState state;
        int nBytes;
        InetSocketAddress address;
        Throwable err;
        Rudder clientRudder;

        public Letter(LetterType type, RudderState st, Rudder clientRd, int n, InetSocketAddress adr, Throwable err) {
            this.type = type;
            this.state = st;
            this.clientRudder = clientRd;
            this.nBytes = n;
            this.address = adr;
            this.err = err;
        }
    }

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
    public static Map<Integer, GrandAgent> agents = new HashMap<>();
    public static List<LifecycleListener> listeners = new ArrayList<>();

    public int selectTimeoutSec = SELECT_TIMEOUT_SEC;
    public final int agentId;
    public Multiplexer netMultiplexer;
    public Multiplexer jobMultiplexer;
    public Multiplexer taxiMultiplexer;
    public SpinMultiplexer spinMultiplexer;
    public Multiplexer spiderMultiplexer;
    public Multiplexer pegionMultiplexer;
    public Recipient recipient;

    public final int maxInboundShips;
    public boolean aborted;
    private boolean anchorable;
    private ArrayList<TimerHandler> timerHandlers = new ArrayList<>();
    CommandReceiver commandReceiver;
    private ArrayList<Runnable> postponeQueue = new ArrayList<>();
    private long lastTimeoutCheck;


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
        this.pegionMultiplexer = new PigeonMultiplexer(this, anchorable);
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
                this.netMultiplexer = this.pegionMultiplexer;
                break;

            case Spin:
            case Taxi:
            case Train:
                throw new Sink("Multiplexer not supported: %s", Harbor.getMultiplexerTypeName(BayServer.harbor.netMultiplexer()));
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
                for(NetworkChannelRudder rd: BayServer.anchorablePortMap.keySet()) {
                    if(netMultiplexer.isNonBlocking()) {
                        try {
                            rd.setNonBlocking();
                        }
                        catch(IOException e) {
                            BayLog.fatal(e);
                        }
                    }
                    netMultiplexer.addRudderState(rd, new RudderState(rd));
                }
            }
            else {
                // Adds server socket  up unanchorable ports
                for (Rudder rd : BayServer.unanchorablePortMap.keySet()) {
                    Port p = BayServer.unanchorablePortMap.get(rd);
                    p.onConnected(agentId, rd);
                }
            }

            boolean busy = true;
            while (true) {
                boolean testBusy = netMultiplexer.isBusy();
                if (testBusy != busy) {
                    busy = testBusy;
                    if(busy) {
                        netMultiplexer.onBusy();
                    }
                    else {
                        netMultiplexer.onFree();
                    }
                }

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
                    if((lastTimeoutCheck - System.currentTimeMillis() % 1000) >= 10)
                        ring();
                }

                //spinMultiplexer.processData();
                while(!letterQueue.isEmpty()) {
                    Letter let;
                    synchronized (letterQueue) {
                        let = letterQueue.remove(0);
                    }

                    switch(let.type) {
                        case Accepted:
                            onAccept(let);
                            break;

                        case Connected:
                            onConnect(let);
                            break;

                        case Read:
                            onRead(let);
                            break;

                        case Wrote:
                            onWrote(let);
                            break;

                        case CloseReq:
                            onCloseReq(let);
                            break;
                    }
                }
            }
        }
        catch (Throwable e) {
            // If error occurs, grand agent ends
            BayLog.fatal(e, "%s Fatal error!", this);
            shutdown();
            System.exit(1);
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
        lastTimeoutCheck = System.currentTimeMillis();
    }

    public void addCommandReceiver(Rudder rd) {
        commandReceiver = new CommandReceiver();
        Transporter comTransporter = new PlainTransporter(netMultiplexer, commandReceiver, true, 8, false);
        commandReceiver.init(agentId, rd, comTransporter);
        netMultiplexer.addRudderState(commandReceiver.rudder, new RudderState(commandReceiver.rudder, comTransporter));
    }

    public void sendAcceptedLetter(RudderState st, Rudder clientRd, Throwable e, boolean wakeup) {
        if(st == null)
            throw new NullPointerException();
        sendLetter(new Letter(LetterType.Accepted, st, clientRd,-1, null, e), wakeup);
    }

    public void sendConnectedLetter(RudderState st, Throwable e, boolean wakeup) {
        if(st == null)
            throw new NullPointerException();
        sendLetter(new Letter(LetterType.Connected, st, null, -1, null, e), wakeup);
    }

    public void sendReadLetter(RudderState st, int n, InetSocketAddress adr, Throwable e, boolean wakeup) {
        if(st == null)
            throw new NullPointerException();
        sendLetter(new Letter(LetterType.Read, st, null, n, adr, e), wakeup);
    }

    public void sendWroteLetter(RudderState st, int n, Throwable e, boolean wakeup) {
        if(st == null)
            throw new NullPointerException();
        sendLetter(new Letter(LetterType.Wrote, st, null, n, null, e), wakeup);
    }

    public void sendCloseReqLetter(RudderState st, boolean wakeup) {
        if(st == null)
            throw new NullPointerException();
        sendLetter(new Letter(LetterType.CloseReq, st, null, -1, null, null), wakeup);
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
        agents.remove(this);
    }

    void abort() {
        BayLog.fatal("%s abort", this);
    }

    void reloadCert() {
        for(Port port : BayServer.anchorablePortMap.values()) {
            if(port.secure()) {
                PortBase pbase = (PortBase)port;
                try {
                    pbase.secureDocker.reloadCert();
                } catch (Exception e) {
                    BayLog.error(e);
                }
            }
        }
    }

    public void printUsage() {
        // print memory usage
    }

    public synchronized void addPostpone(Runnable p) {
        postponeQueue.add(p);
    }

    public int countPostpone() {
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

    private void onAccept(Letter let) throws Throwable {
        Port p = BayServer.anchorablePortMap.get(let.state.rudder);

        try {
            if(let.err != null)
                throw let.err;

            p.onConnected(agentId, let.clientRudder);
        }
        catch (HttpException e) {
            BayLog.error(e);
            try {
                let.clientRudder.close();
            }
            catch (IOException ex) {
                BayLog.error(ex);
            }
        }

        if (!netMultiplexer.isBusy()) {
            let.state.multiplexer.nextAccept(let.state);
        }
    }

    private void onConnect(Letter let) throws Throwable {
        RudderState st = let.state;
        if (st.closed) {
            BayLog.debug("%s Rudder is already closed: rd=%s", this, st.rudder);
            return;
        }

        BayLog.debug("%s connected rd=%s", this, st.rudder);
        NextSocketAction nextAct;
        try {
            if(let.err != null)
                throw let.err;

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

    private void onRead(Letter let) throws Throwable {
        RudderState st = let.state;
        if (st.closed) {
            BayLog.debug("%s Rudder is already closed: rd=%s", this, st.rudder);
            return;
        }

        NextSocketAction nextAct;

        try {
            if(let.err != null) {
                BayLog.debug("%s error on OS read %s", this, let.err);
                throw let.err;
            }

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

    private void onWrote(Letter let) throws Throwable{
        RudderState st = let.state;
        if (st.closed) {
            BayLog.debug("%s Rudder is already closed: rd=%s", this, st.rudder);
            return;
        }

        try {

            if(let.err != null) {
                BayLog.debug("%s error on OS write %s", this, let.err);
                throw let.err;
            }

            BayLog.debug("%s wrote %d bytes rd=%s qlen=%d", this, let.nBytes, st.rudder, st.writeQueue.size());
            st.bytesWrote += let.nBytes;

            if(st.writeQueue.isEmpty())
                throw new IllegalStateException(this + " Write queue is empty: rd=" + st.rudder);

            boolean writeMore = true;
            WriteUnit unit = st.writeQueue.get(0);
            //BayLog.debug("%s wrote buf=%s", this, unit.buf);
            if (unit.buf.hasRemaining()) {
                BayLog.debug("Could not write enough data buf=%s", unit.buf);
                writeMore = true;
            }
            else {
                st.multiplexer.consumeOldestUnit(st);
            }

            synchronized (st.writing) {
                if (st.writeQueue.isEmpty()) {
                    writeMore = false;
                    st.writing[0] = false;
                }
            }

            if (writeMore) {
                st.multiplexer.nextWrite(st);
            }
            else {
                if(st.finale) {
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
        catch(IOException e) {
            BayLog.debug("%s error on wrote");
            st.transporter.onError(st.rudder, e);
            nextAction(st, NextSocketAction.Close, false);
        }
    }

    protected final void onCloseReq(Letter let) {
        RudderState st = let.state;
        BayLog.debug("%s reqClose rd=%s", this, st.rudder);
        if (st.closed) {
            BayLog.debug("%s Rudder is already closed: rd=%s", this, st.rudder);
            return;
        }

        st.multiplexer.closeRudder(st);
        st.access();
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
                if(reading)
                    cancel = true;
                break;

            case Close:
                if(reading)
                    cancel = true;
                st.multiplexer.closeRudder(st);
                break;

            case Suspend:
                if(reading)
                    cancel = true;
                break;
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
        return agents.get(id);
    }

    public static GrandAgent add(int agtId, boolean anchorable) {
        if(agtId == -1)
            agtId = ++maxAgentId;
        BayLog.debug("Add agent: id=%d", agtId);

        if(agtId > maxAgentId)
            maxAgentId = agtId;

        GrandAgent agt = new GrandAgent(agtId, maxShips, anchorable);
        agents.put(agtId, agt);

        listeners.forEach(lis -> lis.add(agt.agentId));
        return agt;
    }

    public static void addLifecycleListener(LifecycleListener lis) {
        listeners.add(lis);
    }


    /////////////////////////////////////////////////////////////////////////////
    // private methods                                                         //
    /////////////////////////////////////////////////////////////////////////////
}
