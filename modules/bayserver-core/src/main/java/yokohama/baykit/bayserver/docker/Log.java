package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.tour.Tour;

import java.io.IOException;

public interface Log {

    void log(Tour tour) throws IOException;

}
