package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.rudder.NetworkChannelRudder;
import yokohama.baykit.bayserver.tour.Tour;

public interface Permission {

    void socketAdmitted(NetworkChannelRudder rd) throws HttpException;

    void tourAdmitted(Tour tour) throws HttpException;
}
