package yokohama.baykit.bayserver.docker.builtin;

import yokohama.baykit.bayserver.tour.Tour;

abstract class LogItem {


    /**
     * initialize
     */
    void init(String param) {
    }

    /**
     * Print log
     */
    abstract String getItem(Tour tour);



}
