package yokohama.baykit.bayserver.docker.barge;

import yokohama.baykit.bayserver.*;
import yokohama.baykit.bayserver.bcf.BcfElement;
import yokohama.baykit.bayserver.bcf.BcfKeyVal;
import yokohama.baykit.bayserver.docker.Barge;
import yokohama.baykit.bayserver.docker.Docker;
import yokohama.baykit.bayserver.docker.base.DockerBase;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.rudder.SelectableChannelRudder;
import yokohama.baykit.bayserver.rudder.WritableByteChannelRudder;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class MemBargeDocker extends DockerBase implements Barge {

    public class MemCargo implements Cargo {
        static final int LOADING = 1;
        static final int LOADED = 2;
        static final int EXCEEDED = 3;

        String path;
        int length;
        int status;
        SimpleBuffer buf = new SimpleBuffer();
        Headers headers = new Headers();
        long lastAccessedTimeMillis;
        ArrayList<Rudder> waiters = new ArrayList<>();

        public MemCargo(String path) {
            this.path = path;
            this.length = 0;
            this.status = LOADING;
        }


        ////////////////////////////////////////////
        // Implements Cargo
        ////////////////////////////////////////////

        @Override
        public String path() {
            return path;
        }

        @Override
        public Headers headers() {
            return headers;
        }

        @Override
        public byte[] content() {
            return buf.bytes();
        }

        @Override
        public int length() {
            return buf.length();
        }

        @Override
        public boolean onBarge() {
            return status == LOADED;
        }

        @Override
        public boolean exceeded() {
            return status == EXCEEDED;
        }

        @Override
        public void saveHeaders(Headers headers) {
            if(onBarge())
                throw new IllegalStateException("already saved");
            if(exceeded())
                return;

            headers.copyTo(this.headers);
        }

        @Override
        public void saveContent(byte[] bytes, int offset, int len) {
            if(onBarge())
                throw new IllegalStateException("already saved");
            if(exceeded())
                return;

            BayLog.debug("%s save content len=%d", this, len);
            length += len;

            if(length > BayServer.harbor.maxCargoSize()) {
                BayLog.debug("%s cargo exceeded: len=%d max=%d", this, length, BayServer.harbor.maxCargoSize());
                status = EXCEEDED;
                buf.reset();
                return;
            }
            else {
                buf.put(bytes, offset, len);
            }
        }

        @Override
        public synchronized void endSave() {
            if(onBarge())
                throw new IllegalStateException("already saved");
            if(exceeded())
                return;

            BayLog.debug("%s end save", this);
            status = LOADED;
            addTotal(length);

            ByteBuffer b = ByteBuffer.allocate(1);
            for(Rudder rd: waiters) {
                try {
                    BayLog.debug("%s notify waiter", this);
                    rd.write(b);
                }
                catch(IOException e) {
                    BayLog.error(e);
                }
            }
        }

        @Override
        public synchronized void releaseRudder(Rudder rudder) {
            waiters.remove(rudder);
        }

        ////////////////////////////////////////////
        // Private methods
        ////////////////////////////////////////////
        private void access() {
            lastAccessedTimeMillis = RoughTime.currentTimeMillis();
        }

        private boolean expired() {
            return waiters.isEmpty() && RoughTime.currentTimeMillis() - lastAccessedTimeMillis > (long)BayServer.harbor.cargoLifespanSec() * 1000;
        }

        private void addWaiter(Rudder rd) {
            waiters.add(rd);
        }
    }

    String name;
    int capacity = 32 * 1024 * 1024; // 32M bytes
    int totalSize;

    // Enable "Access Order" (LRU) mode by setting the 3rd argument to true.
    // In this mode, the most recently accessed entry moves to the end of the list.
    private final LinkedHashMap<String, MemCargo> cargoMap = new LinkedHashMap<>(16, 0.75f, true);

    @Override
    public String toString() {
        return "MemBargeDocker[" + name + "]";
    }

    ///////////////////////////////////////////////////////////////////////
    // Implements Docker
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void init(BcfElement elm, Docker parent) throws ConfigException {
        super.init(elm, parent);
        this.name = elm.arg;
        if(StringUtil.empty(name))
            this.name = "*";
    }

    ///////////////////////////////////////////////////////////////////////
    // Implements DockerBase
    ///////////////////////////////////////////////////////////////////////

    @Override
    public boolean initKeyVal(BcfKeyVal kv) throws ConfigException {
        switch (kv.key.toLowerCase()) {
            default:
                return super.initKeyVal(kv);

            case "capacity":
                capacity = StringUtil.parseSize(kv.value);
                break;

        }
        return true;
    }

    ////////////////////////////////////////////
    // Implements Barge
    ////////////////////////////////////////////

    @Override
    public String name() {
        return name;
    }


    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public synchronized Pair<Cargo, Rudder> getCargo(Tour tour) {
        String path = tour.req.uri;
        MemCargo cgo = cargoMap.get(path);
        Rudder sourceRd = null;

        if(cgo != null && cgo.waiters.isEmpty() && cgo.expired()) {
            totalSize -= cgo.length();
            cargoMap.remove(path);
            cgo = null;
        }

        if (cgo == null) {
            cgo = new MemCargo(path);
            cargoMap.put(path, cgo);
            tour.res.directBoarding = false; // Don't use OS cache (sendfile API)
        }
        else {
            if (cgo.status == MemCargo.LOADING) {
                // Cargo is loading
                // Wait until cargo is loaded.
                BayLog.debug("%s Cannot start tour (file reading)", tour);

                Rudder waitRd;
                try {
                    Pipe pip = Pipe.open();
                    sourceRd = new SelectableChannelRudder(pip.source());
                    sourceRd.setNonBlocking();
                    waitRd = new WritableByteChannelRudder(pip.sink());
                }
                catch (IOException e) {
                    throw new Sink("Cannot create pipe: %s", e);
                }
                cgo.addWaiter(waitRd);

            }
            else {
                // Cargo exceeds cache limit — follow harbor's directBoarding setting
                // (sendfile for zero-copy transfer instead of byte-copy through protocol stack).
                // Cargo fits in cache — disable directBoarding so content flows through
                // userspace for in-memory serving.
                tour.res.directBoarding = cgo.exceeded() && BayServer.harbor.directBoarding();
            }
        }
        cgo.access();
        return new Pair<>(cgo, sourceRd);
    }

    ////////////////////////////////////////////
    // Private methods
    ////////////////////////////////////////////

    /**
     * Add the size of the newly loaded cargo to the total, then evict
     * old entries (insertion order) until the total falls within capacity.
     * Entries with active waiters are skipped to avoid disrupting
     * in-progress cargo loads.
     */
    private synchronized void addTotal(int len) {
        totalSize += len;
        BayLog.trace("%s addTotal=%d", this, totalSize);
        Iterator<Map.Entry<String, MemCargo>> it = cargoMap.entrySet().iterator();
        while (totalSize > capacity && it.hasNext()) {
            Map.Entry<String, MemCargo> eldest = it.next();
            if (eldest.getValue().waiters.isEmpty()) {
                BayLog.trace("%s Evict cargo: %s len=%d total=%d", this, eldest.getKey(), eldest.getValue().length(), totalSize);
                totalSize -= eldest.getValue().length();
                it.remove();
            }
            else {
                BayLog.trace("%s Skip cargo (has waiters): %s", this, eldest.getKey());
            }
        }
    }
}
