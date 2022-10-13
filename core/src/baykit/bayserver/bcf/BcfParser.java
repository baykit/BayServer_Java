package baykit.bayserver.bcf;

import baykit.bayserver.BayMessage;
import baykit.bayserver.BayServer;
import baykit.bayserver.Symbol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class BcfParser {
    
    int lineNo;
    BufferedReader br;
    LineInfo prevLineInfo;
    String fileName;
    ArrayList<Integer> indentMap = new ArrayList<>();
    
    private void pushIndent(int spCount) {
        indentMap.add(spCount);
    }
    
    private void popIndent() {
        indentMap.remove(indentMap.size() - 1);
    }
    
    private int getIndent(int spCount) throws ParseException{
        if(indentMap.isEmpty())
            pushIndent(spCount);
        else if(spCount > indentMap.get(indentMap.size() - 1)) {
            pushIndent(spCount);
        }
        int indent = indentMap.indexOf(spCount);
        if(indent == -1)
            throw new ParseException(fileName, lineNo, BayMessage.get(Symbol.PAS_INVALID_INDENT));
        return indent;
    }
    
    public BcfDocument parse(String file) throws ParseException {
        BcfDocument doc = new BcfDocument();
        ArrayList<BcfObject> currentContentList = doc.contentList;
        ArrayList<BcfObject> parentContentList = null;

        fileName = file;
        lineNo = 0;
        try {
            br = new BufferedReader(
                    new InputStreamReader(
                            new FileInputStream(file),
                            StandardCharsets.UTF_8));
            parseSameLevel(doc.contentList, 0);
        }
        catch(ParseException e) {
            throw e;
        }
        catch(IOException e) {
            throw new ParseException(file, lineNo, e);
        }
        finally {
            if(br != null)
                try {br.close(); } catch(IOException e) {}
        }
        return doc;
    }
    
    private class LineInfo{

        public final BcfObject lineObj;
        public final int indent;
        
        public LineInfo(BcfObject lineObj, int indent) {
            this.lineObj = lineObj;
            this.indent = indent;
        }
    }
    
    private LineInfo parseSameLevel(ArrayList<BcfObject> curList, int indent) throws ParseException, IOException {
        boolean objectExistsInSameLevel = false;
        while (true) {
            LineInfo lineInfo;
            if(prevLineInfo != null) {
                lineInfo = prevLineInfo;
                prevLineInfo = null;
                //BayServer.debug("returned line=" + lineInfo.lineObj + " indent=" + lineInfo.indent + " cur=" + indent);
            }
            else {
                String line = br.readLine();
                lineNo++;
                if (line == null)
                    break;

                if(line.trim().startsWith("#") || line.trim().equals("")) {
                    // comment or empty line
                    continue;
                }

                lineInfo = parseLine(lineNo, line);
                //BayServer.debug("line=" + line + " indent=" + lineInfo.indent + " cur=" + indent);
            }
            
            if(lineInfo == null) {
                // comment or empty
                continue;
            }
            else if(lineInfo.indent > indent) {
                // lower level
                throw new ParseException(fileName, lineNo, BayMessage.get(Symbol.PAS_INVALID_INDENT));
            }
            else if(lineInfo.indent < indent) {
                // upper level
                prevLineInfo = lineInfo;
                if(objectExistsInSameLevel)
                    popIndent();
                return lineInfo;
            }
            else {
                objectExistsInSameLevel = true;
                // same level
                if(lineInfo.lineObj instanceof BcfElement) {                     
                    curList.add(lineInfo.lineObj);

                    BcfElement elm = (BcfElement)lineInfo.lineObj;
                    LineInfo lastLineInfo = parseSameLevel(elm.contentList, lineInfo.indent + 1);
                    if(lastLineInfo == null) {
                        // EOF
                        popIndent();
                        return null;
                    }
                    else {
                        // Same level
                        continue;
                    }
                }
                else {                     
                    curList.add(lineInfo.lineObj);                     
                    continue;
                }
            }
        }
        popIndent();
        return null;
    }
    
    private LineInfo parseLine(int lineNo, String line) throws ParseException {
        
        int spCount;
        for(spCount = 0; spCount < line.length(); spCount++) {
            char c = line.charAt(spCount);
            if(!Character.isWhitespace(c))
                break;

            if(c != ' ')
                throw new ParseException(fileName, lineNo, BayMessage.get(Symbol.PAS_INVALID_WHITESPACE));
        }
        int indent = getIndent(spCount);
        
        line = line.substring(spCount);
        line = line.trim();
        
        if(line.startsWith("[")) {
            int closePos = line.indexOf("]");
            if(closePos == -1) {
                throw new ParseException(fileName, lineNo, BayMessage.get(Symbol.PAS_BRACE_NOT_CLOSED));
            }
            if(!line.endsWith("]")) {
                throw new ParseException(fileName, lineNo, BayMessage.get(Symbol.PAS_INVALID_LINE));
            }
            BcfKeyVal keyVal = parseKeyVal(line.substring(1, closePos), lineNo);
            return new LineInfo(
                    new BcfElement(
                            keyVal.key, 
                            keyVal.value,
                            fileName,
                            lineNo),
                    indent);
        }
        else {
            return new LineInfo(parseKeyVal(line, lineNo), indent);
        }
    }
    
    private BcfKeyVal parseKeyVal(String line, int lineNo) {
        int spPos = line.indexOf(' ');
        String key = spPos == -1 ? line : line.substring(0, spPos);
        String val = spPos == -1 ? "" : line.substring(spPos).trim();
        return new BcfKeyVal(key, val, fileName,lineNo);
    }
}
