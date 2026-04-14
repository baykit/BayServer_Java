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
import yokohama.baykit.bayserver.rudder.Rudder;
import yokohama.baykit.bayserver.tour.ContentConsumeListener;
import yokohama.baykit.bayserver.tour.ReqContentHandler;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.util.HttpStatus;
import yokohama.baykit.bayserver.util.Mimes;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;

public class FileContentHandler implements ReqContentHandler {

    final Tour tour;
    final Path path;
    final String charset;
    final String mimeType;
    final boolean directBoarding;
    final FileStore fileStore;
    final boolean listFiles;
    boolean abortable;

    public FileContentHandler(
            Tour tur,
            Path path,
            String charset,
            FileStore st,
            boolean listFiles) {
        this.tour = tur;
        this.path = path;
        this.charset = charset;
        this.abortable = true;
        this.fileStore = st;
        this.listFiles = listFiles;
        this.directBoarding = st != null;


        String mtype = null;
        String rname = path.getFileName().toString();
        int pos = rname.lastIndexOf('.');
        if (pos >= 0) {
            String ext = rname.substring(pos + 1).toLowerCase();
            mtype = Mimes.getType(ext);
        }

        if (mtype == null)
            mtype = "application/octet-stream";

        if (mtype.startsWith("text/") && charset != null)
            mtype = mtype + "; charset=" + charset;
        mimeType = mtype;
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
        BayLog.debug("%s reqStartTour", tour);

        FileSendInfo f = null;
        if(fileStore != null) {
            f = fileStore.get(path);
        }

        if(f == null) {
            if (!Files.isDirectory(path)) {
                try {
                    f = getFileSendInfo(path, fileStore != null);
                    if(fileStore != null) {
                        fileStore.put(path, f);
                    }
                }
                catch (FileNotFoundException | NoSuchFileException e) {
                    throw new HttpException(HttpStatus.NOT_FOUND, path.toString());
                }
                catch (Exception e) {
                    BayLog.error(e);
                    throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, path.toString());
                }
            }
        }

        if(f == null) {
            handleDirectory(tour, path);
        }
        else if(directBoarding) {
            skipFormalitiesAndTransmit(f);
        }
        else {
            transmitWithFormalities(f);
        }
    }


    private void handleDirectory(Tour tur, Path path) throws HttpException {
        if(listFiles) {
            DirectoryTrain train = new DirectoryTrain(tur, path);
            train.startTour();
        }
        else {
            throw new HttpException(HttpStatus.FORBIDDEN, "Directory scan is prohibited");
        }
    }


    private void transmitWithFormalities(FileSendInfo file) throws HttpException {

        try {
            tour.res.headers.setContentType(mimeType);
            tour.res.headers.setContentLength(Files.size(path));

            tour.res.sendHeaders(Tour.TOUR_ID_NOCHECK);

            int bufsize = tour.ship.protocolHandler.maxResPacketDataSize();
            GrandAgent agt = GrandAgent.get(tour.ship.agentId);
            Multiplexer mpx;

            switch (BayServer.harbor.fileMultiplexer()) {
                case Spin: {
                    mpx = agt.spinMultiplexer;
                    break;
                }

                case Job: {
                    mpx = agt.jobMultiplexer;
                    break;
                }

                case Taxi: {
                    mpx = agt.taxiMultiplexer;
                    break;
                }

                case Pigeon: {
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

            sendFileShip.init(file.rudder, tp, tour);
            int sid = sendFileShip.id();
            tour.res.setConsumeListener((len, resume) -> {
                if (resume) {
                    sendFileShip.resumeRead(sid);
                }
            });

            RudderState st = RudderStateStore.getStore(agt.agentId).rent();
            st.init(file.rudder, tp);
            mpx.addRudderState(file.rudder, st);
            mpx.reqRead(file.rudder);

        }
        catch (FileNotFoundException e) {
            throw new HttpException(HttpStatus.NOT_FOUND, path.toString());
        }
        catch (Exception e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, path.toString());
        }
    }

    private void skipFormalitiesAndTransmit(FileSendInfo file) throws HttpException {
        int turId = tour.id();
        tour.res.setConsumeListener(new ContentConsumeListener() {
            @Override
            public void contentConsumed(int len, boolean resume) {
                try {
                    tour.res.endResContent(turId);
                }
                catch(IOException e) {
                    BayLog.debug(e);
                }
            }
        });
        tour.res.headers.setContentType(mimeType);
        tour.res.headers.setContentLength(file.length);
        try {
            tour.res.sendHeaders(Tour.TOUR_ID_NOCHECK);
            tour.res.transferContent(Tour.TOUR_ID_NOCHECK, file.rudder, 0, file.length);
            //tour.res.endResContent(Tour.TOUR_ID_NOCHECK);
        } catch (IOException e) {
            BayLog.error(e);
            throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, path.toString());
        }
    }

    private FileSendInfo getFileSendInfo(Path path, boolean skipFormalities) throws IOException {
        Rudder rd;

        //BayLog.info("open file: %s", path.toString());

        if(skipFormalities) {
            FileChannel ch = FileChannel.open(path);
            rd = new ReadableByteChannelRudder(ch);
        }
        else {
            switch (BayServer.harbor.fileMultiplexer()) {
                case Spin:
                case Pigeon: {
                    AsynchronousFileChannel ch =
                            AsynchronousFileChannel.open(path, StandardOpenOption.READ);
                    rd = new AsynchronousFileChannelRudder(ch);
                    break;
                }

                case Job:
                case Taxi: {
                    InputStream in = new FileInputStream(path.toFile());
                    ReadableByteChannel ch = Channels.newChannel(in);
                    rd = new ReadableByteChannelRudder(ch);
                    break;
                }

                default:
                    throw new Sink();
            }
        }

        return new FileSendInfo(rd, (int)Files.size(path));
    }

}
