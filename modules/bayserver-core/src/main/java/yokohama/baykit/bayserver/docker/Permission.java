package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.rudder.NetworkRudder;
import yokohama.baykit.bayserver.tour.Tour;

public interface Permission {

    void socketAdmitted(NetworkRudder rd) throws HttpException;

    void tourAdmitted(Tour tour) throws HttpException;
}
