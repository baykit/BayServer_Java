package yokohama.baykit.bayserver.docker.servlet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public abstract class MappingStore {

    public static class Mapping {
        public String name;
        public String pattern;
        public MappingMatcher matcher;

        MappingMatcher.Result match(String path) {
            if(matcher == null)
                matcher = new MappingMatcher(pattern);
            return matcher.isMatch(path);
        }
    }


    public static class MatchResult {
        public Object matchedObj;
        public String servletPath;
        public String pathInfo;
        public boolean async;

        public MatchResult(Object matchedObj, String servletPath, String pathInfo, boolean async) {
            this.matchedObj = matchedObj;
            this.servletPath = servletPath;
            this.pathInfo = pathInfo;
            this.async = async;
        }
    }

    public ArrayList<Mapping> mappings = new ArrayList<>();

    public final void addMapping(String name, String urlPattern) {
        Mapping m = new Mapping();
        m.name = name;
        m.pattern = urlPattern;

        mappings.add(m);
    }



    public boolean hasPattern(String name, String urlPattern) {
        Set<String> res = new HashSet<>();
        for(MappingStore.Mapping m : mappings) {
            if(m.name.equals(name) && m.pattern.equals(urlPattern))
                return true;
        }
        return false;
    }

}
