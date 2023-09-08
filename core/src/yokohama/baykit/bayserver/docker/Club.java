package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.tour.Tour;

public interface Club {

    /**
     * Get the file name part of club
     * @return
     */
    String fileName();

    /**
     * Get the ext (file extension part) of club
     * @return
     */
    String extension();

    /**
     * Check if file name matches this club
     * @param fname
     * @return
     */
    boolean matches(String fname);

    /**
     * Get charset of club
     * @return
     */
    String charset();

    /**
     * Check if this club decodes PATH_INFO
     * @return
     */
    boolean decodePathInfo();

    /**
     * Arrive 
     */
    void arrive(Tour tour) throws HttpException;

}
