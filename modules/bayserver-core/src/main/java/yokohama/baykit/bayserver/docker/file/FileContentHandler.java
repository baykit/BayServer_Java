package yokohama.baykit.bayserver.docker.file;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.BayServer;
import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.GrandAgent;
import yokohama.baykit.bayserver.agent.multiplexer.PlainTransporter;
import yokohama.baykit.bayserver.common.RudderState;
import yokohama.baykit.bayserver.common.Multiplexer;
import yokohama.baykit.bayserver.common.RudderStateStore;
import yokohama.baykit.bayserver.rudder.AsynchronousFileChannelRudder;
import yokohama.baykit.bayserver.rudder.ReadableByteChannelRudder;
import yokohama.baykit.bayserver.rudder.SelectableChannelRudder;
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.rudder.WritableByteChannelRudder;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.Mimes;

import java.io.*;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class FileContentHandler implements ReqContentHandler {

    final Tour tour;
    final File path;
    final String charset;
    String mimeType;
    boolean abortable;
    FileStore store;
    FileContent fileContent;

    public FileContentHandler(Tour tur, FileStore store, File path, String charset) {
        this.tour = tur;
        this.store = store;
        this.path = path;
        this.charset = charset;
        this.abortable = true;


        String rname = path.getName();
        int pos = rname.lastIndexOf('.');
        if (pos >= 0) {
            String ext = rname.substring(pos + 1).toLowerCase();
            mimeType = Mimes.getType(ext);
        }

        if (mimeType == null)
            mimeType = "application/octet-stream";

        if (mimeType.startsWith("text/") && charset != null)
            mimeType = mimeType + "; charset=" + charset;
    }

    ///////////////////////////////////////////////////////////////////////
    // Implements ReqContentHandler
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void onReadReqContent(Tour tur, byte[] buf, int start, int len, ContentConsumeListener lis) throws IOException {
        BayLog.debug("%s file:onReadContent(Ignore) len=%d", tur, len);
        tur.req.consumed(tur.tourId, len, lis);
    }

    @Override
    public void onEndReqContent(Tour tur) throws IOException, HttpException {
        BayLog.debug("%s file:endContent", tur);
        reqStartTour();
        abortable = false;
    }

    @Override
    public boolean onAbortReq(Tour tur) {
        BayLog.debug("%s file:onAbort aborted=%s", tur, abortable);
        return abortable;
    }


    ////////////////////////////////////////////////////////////////////////////////
    // Sending file methods
    ////////////////////////////////////////////////////////////////////////////////

    public synchronized void reqStartTour() throws HttpException {
        boolean[] reading = new boolean[1];
        FileStore.FileContentStatus status = store.get(path, reading);
        fileContent = status.fileContent;

        BayLog.debug("%s file content status: %d", tour, status.status);
        switch (status.status) {
            case FileStore.FileContentStatus.STARTED:
            case FileStore.FileContentStatus.EXCEEDED:
                sendFileAsync();
                break;

            case FileStore.FileContentStatus.READING: {
                // Wait file loaded
                BayLog.debug("%s Cannot start tour (file reading)", tour);

                GrandAgent agt = GrandAgent.get(tour.ship.agentId);
                WaitFileShip waitFileShip = new WaitFileShip();
                PlainTransporter tp = new PlainTransporter(
                        agt.spiderMultiplexer,
                        waitFileShip,
                        true,
                        8192,
                        false);

                Rudder sourceRd;
                Rudder waitRd;
                try {
                    Pipe pipe = Pipe.open();
                    sourceRd = new SelectableChannelRudder(pipe.source());
                    sourceRd.setNonBlocking();
                    waitRd = new WritableByteChannelRudder(pipe.sink());
                }
                catch (IOException e) {
                    throw new Sink("Fatal error: %s", e);
                }
                waitFileShip.init(sourceRd, tp, tour, fileContent, this);
                tour.res.setConsumeListener(ContentConsumeListener.devNull);

                RudderState st = RudderStateStore.getStore(agt.agentId).rent();
                st.init(sourceRd, tp);
                agt.spiderMultiplexer.addRudderState(sourceRd, st);
                agt.spiderMultiplexer.reqRead(sourceRd);

                fileContent.addWaiter(waitRd);
                break;
            }

            case FileStore.FileContentStatus.COMPLETED: {
                sendFileFromCache();
                break;
            }

            default:
                throw new Sink();
        }
    }

    public void sendFileAsync() throws HttpException {
        //resHeaders.setStatus(HttpStatus.OK);
        tour.res.headers.setContentType(mimeType);
        tour.res.headers.setContentLength(path.length());
        try {
            tour.res.sendHeaders(Tour.TOUR_ID_NOCHECK);

            int bufsize = tour.ship.protocolHandler.maxResPacketDataSize();
            GrandAgent agt = GrandAgent.get(tour.ship.agentId);
            Multiplexer mpx = null;
            Rudder rd = null;

            switch (BayServer.harbor.fileMultiplexer()) {
                case Spin: {
                    AsynchronousFileChannel ch =
                            AsynchronousFileChannel.open(Paths.get(path.getPath()), StandardOpenOption.READ);
                    rd = new AsynchronousFileChannelRudder(ch);
                    mpx = agt.spinMultiplexer;

                    break;
                }

                case Job: {
                    InputStream in = new FileInputStream(path);
                    ReadableByteChannel ch = Channels.newChannel(in);
                    rd = new ReadableByteChannelRudder(ch);
                    mpx = agt.jobMultiplexer;

                    break;
                }

                case Taxi: {
                    InputStream in = new FileInputStream(path);
                    ReadableByteChannel ch = Channels.newChannel(in);
                    rd = new ReadableByteChannelRudder(ch);
                    mpx = agt.taxiMultiplexer;

                    break;
                }

                case Pigeon: {
                    AsynchronousFileChannel ch =
                            AsynchronousFileChannel.open(Paths.get(path.getPath()), StandardOpenOption.READ);
                    rd = new AsynchronousFileChannelRudder(ch);
                    mpx = agt.pegionMultiplexer;

                    break;
                }

                default:
                    throw new Sink();
            }

            SendFileShip sendFileShip = new SendFileShip();
            PlainTransporter tp = new PlainTransporter(
                    mpx,
                    sendFileShip,
                    true,
                    8192,
                    false);

            sendFileShip.init(rd, tp, tour, fileContent);
            int sid = sendFileShip.id();
            tour.res.setConsumeListener((len, resume) -> {
                if (resume) {
                    sendFileShip.resumeRead(sid);
                }
            });

            RudderState st = RudderStateStore.getStore(agt.agentId).rent();
            st.init(rd, tp);
            mpx.addRudderState(rd, st);
            mpx.reqRead(rd);

        }
        catch (FileNotFoundException e) {
            throw new HttpException(HttpStatus.NOT_FOUND, path.getPath());
        }
        catch (IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, path.getPath());
        }
    }

    public void sendFileFromCache() throws HttpException {
        tour.res.setConsumeListener(ContentConsumeListener.devNull);
        tour.res.headers.setContentType(mimeType);
        tour.res.headers.setContentLength(path.length());
        try {
            tour.res.sendHeaders(Tour.TOUR_ID_NOCHECK);
            tour.res.sendResContent(Tour.TOUR_ID_NOCHECK, fileContent.content.array(), 0, fileContent.content.array().length);
            tour.res.endResContent(Tour.TOUR_ID_NOCHECK);

        } catch (IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, fileContent.path.getPath());
        }
    }

}
