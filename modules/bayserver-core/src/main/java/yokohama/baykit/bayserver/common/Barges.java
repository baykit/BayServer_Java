package yokohama.baykit.bayserver.common;

import yokohama.baykit.bayserver.docker.Barge;
import yokohama.baykit.bayserver.docker.City;

import java.util.ArrayList;
import java.util.List;

public class Barges {

    /** Default barge docker */
    Barge anyBarge;

    /** Barge dockers */
    public List<Barge> barges = new ArrayList<>();

    public void add(Barge b) {
        if(b.name().equals("*"))
            anyBarge = b;
        else
            barges.add(b);
    }

    public Barge findBarge(String path) {
        // Check exact match
        for(Barge b : barges) {
            if(match(b, path))
                return b;
        }

        return anyBarge;
    }

    boolean match(Barge b, String path) {
        return true;
    }
}
