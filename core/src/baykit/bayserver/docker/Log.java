package baykit.bayserver.docker;

import baykit.bayserver.tour.Tour;

import java.io.IOException;

public interface Log {

    void log(Tour tour) throws IOException;

}
