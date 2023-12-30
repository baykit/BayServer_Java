package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.BayLog;
import yokohama.baykit.bayserver.Sink;
import yokohama.baykit.bayserver.agent.ChannelListener;
import yokohama.baykit.bayserver.agent.NextSocketAction;
import yokohama.baykit.bayserver.tour.Tour;
import yokohama.baykit.bayserver.train.Train;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ReadStreamTrain extends Train {

    InputStream input;
    ChannelListener<InputStream> channelListener;
    Tour tour;
    boolean available;

    public ReadStreamTrain(InputStream input, ChannelListener<InputStream> channelListener, Tour tur) throws FileNotFoundException {
        this.input = input;
        this.channelListener = channelListener;
        this.tour = tur;
        this.available = true;

        tur.res.setConsumeListener((len, resume) -> {
            if(resume)
                available = true;
        });
    }

    @Override
    public void depart() {

        try {
            while_break:
            while (true) {
                NextSocketAction act = channelListener.onReadable(input);

                switch(act) {
                    case Continue:
                    case Suspend:
                        while (!available) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                BayLog.error(e);
                                break while_break;
                            }
                        }
                        continue;

                    case Close:
                        break while_break;

                    default:
                        throw new Sink();
                }
            }
        }
        catch(Throwable e) {
            channelListener.onError(input, e);
        }

        try {
            input.close();
        }
        catch(IOException e) {
            BayLog.error(e);
        }
        channelListener.onClosed(input);
    }
}
