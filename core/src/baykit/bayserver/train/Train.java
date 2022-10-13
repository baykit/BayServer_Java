package baykit.bayserver.train;

import baykit.bayserver.BayLog;
import baykit.bayserver.HttpException;
import baykit.bayserver.tour.Tour;
import baykit.bayserver.util.Counter;

import java.io.IOException;

public abstract class Train implements Runnable {

    protected abstract void depart() throws HttpException;

    public final Tour tour;
    public final int tourId;
    public final int trainId;
    static Counter trainIdCounter = new Counter();

    public Train(Tour tur) {
        this.tour = tur;
        this.tourId = tur.tourId;
        this.trainId = trainIdCounter.next();
    }

    @Override
    public void run() {
        BayLog.debug("%s Start train (%s) on: %s", this, tour, Thread.currentThread().getName());
        try {
            try {
                depart();
            } catch (HttpException e) {
                tour.res.sendHttpException(tourId, e);
            } catch (Throwable e) {
                BayLog.error(e);
                tour.res.endContent(tourId);
            }
        } catch (IOException e) {
            // print unhandled error
            BayLog.error(e, tour + " error");
        }
        BayLog.debug("%s End train on: %s", this, Thread.currentThread().getName());
    }
}
