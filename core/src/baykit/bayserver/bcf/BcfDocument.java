package baykit.bayserver.bcf;

import java.util.ArrayList;

public class BcfDocument {
    
    public ArrayList<BcfObject> contentList = new ArrayList<>();
    
    public void print() {
        printContentList(contentList, 0);
    }
    
    private void printContentList(ArrayList<BcfObject> list, int indent) {
        for(Object o: list) {
            printIndent(indent);
            if(o instanceof BcfElement) {
                BcfElement elm = (BcfElement)o;
                System.out.print("Element(" + elm.name + "," + elm.arg + "){\n");
                printContentList(elm.contentList, indent + 1);
                printIndent(indent);
                System.out.println("}\n");
            }
            else {
                BcfKeyVal kv = (BcfKeyVal)o;
                printKeyVal(kv);
                System.out.print("\n");
            }
        }
    }
    
    private void printKeyVal(BcfKeyVal kv) {
        System.out.print("KeyVal(" + kv.key + "=" + kv.value +")");
    }
    
    private void printIndent(int indent) {
        for(int i = 0; i < indent; i++) {
            System.out.print(" ");
        }
    }
}
