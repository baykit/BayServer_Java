
package yokohama.baykit.bayserver.docker.servlet;

import yokohama.baykit.bayserver.util.URLEncoder;

/**
 * <p>
 * This class checks if an URL matches a URL-pattern. This URL-pattern is
 * specified in &lt;servlet-mapping> and &lt;filter-mapping> of web.xml and
 * &lt;threads> of system.xml.
 * </p>
 * 
 * <pre>
 * 
 *  
 *   Mapping rules are as follows
 *     pattern       prefix     path  ext   type
 *     ------------------------------------------------
 *     /catalog      /catalog   null  null  EXACT_MATCH
 *     /catalog/     /catalog/  null  null  EXACT_MATCH
 *     /catalog/*    /catalog/   *    null  PATH_MAPPING
 *     *.foo         null       null  .foo  EXTENTION_MAPPING
 *   
 *  
 * </pre>
 *  
 */
public class MappingMatcher {

    /**
     * Pattern type, which matches an exact string.
     */
    public static final int EXACT_MATCH = 1;

    /**
     * Pattern type, which matches a path beginnig with a leading prefix.
     */
    public static final int PATH_MAPPING = 2;

    /**
     * Pattern type, which matches a path with a specific suffix.
     */
    public static final int EXTENTION_MAPPING = 3;

    /**
     * The URL pattern
     */
    private final String pattern;

    /**
     * The prefix of pattern
     */
    private final String prefix;

    /**
     * The extension of pattern. (includes dot)
     */
    private final String ext;

    /**
     * The path of pattern
     */
    private final String path;

    /**
     * The type of pattern
     */
    private final int type;

    /**
     * The extension of the pattern. (includes dot)
     */
    public String getExt() {
        return ext;
    }

    /**
     * The path of the pattern
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * The type of the pattern
     */
    public int getType() {
        return type;
    }

    /**
     * The pattern
     */
    public String getPattern() {
        return pattern;
    }
    
    
    public static class Result {
        public String servletPath;
        public String pathInfo;

        public Result(String servletPath, String pathInfo) {
            this.servletPath = servletPath;
            this.pathInfo = pathInfo;
        }
    }

    /**
     * Constructor
     * 
     * @param pattern
     *            the URL pattern
     */
    public MappingMatcher(String pattern) {
        this.pattern = pattern;

        pattern = URLEncoder.encodeTilde(pattern);

        // Check the path mapping
        if (pattern.startsWith("/") && pattern.endsWith("/*")) {
            type = PATH_MAPPING;
            prefix = pattern.substring(0, pattern.length() - 1);
            path = "*";
            ext = null;
        }

        // Check the extension mapping
        else if (pattern.startsWith("*.")) {

            type = EXTENTION_MAPPING;
            prefix = null;
            path = null;
            // Extension includes dot
            ext = pattern.substring(1);
        }

        // Check the root path mapping
        else if (pattern.equals("/")) {
            type = PATH_MAPPING;
            prefix = "/";
            path = null;
            ext = null;
        }

        // Default exact match
        else {
            type = EXACT_MATCH;
            prefix = pattern;
            path = null;
            ext = null;
        }
    }

    /**
     * do matching
     * 
     * @param testPath
     *            test URL
     * @return true if matched
     */
    public Result isMatch(String testPath) {

        switch (type) {
        case EXACT_MATCH:
            if(testPath.equals(prefix)) {
                return new Result(prefix, null);
            }
            else {
                return null;
            }

        case PATH_MAPPING:
            if(testPath.startsWith(prefix)) {
                return new Result(prefix.substring(0, prefix.length() - 1), testPath.substring(prefix.length() - 1));
            }
            else {
                return null;
            }

        case EXTENTION_MAPPING:
            if(testPath.endsWith(ext)) {
                return new Result(testPath, null);
            }
            else {
                return null;
            }

        default:
            throw new Error();
        }
    }

}