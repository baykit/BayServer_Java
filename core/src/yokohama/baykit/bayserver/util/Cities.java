package yokohama.baykit.bayserver.util;

import yokohama.baykit.bayserver.docker.City;

import java.util.ArrayList;
import java.util.List;

public class Cities {

    /** Default city docker */
    City anyCity;

    /** City dockers */
    public List<City> cities = new ArrayList<>();

    public void add(City c) {
        if(c.name().equals("*"))
            anyCity = c;
        else
            cities.add(c);
    }

    public City findCity(String name) {
        // Check exact match
        for(City c : cities) {
            if(c.name().equals(name))
                return c;
        }

        return anyCity;
    }

    public List<City> cities() {
        List<City> ret = new ArrayList<>(cities);
        if(anyCity != null)
            ret.add(anyCity);
        return ret;
    }
}
