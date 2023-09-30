package yokohama.baykit.bayserver.docker.http.h2;

import yokohama.baykit.bayserver.util.KeyVal;

import java.util.ArrayList;

public class HeaderBlockBuilder {

    public HeaderBlock buildHeaderBlock(String name, String value, HeaderTable tbl) {
        ArrayList<Integer> idxList = tbl.get(name);

        HeaderBlock blk = null;
        for(int idx : idxList) {
            KeyVal kv = tbl.get(idx);
            if(kv != null && value.equals(kv.value)) {
                blk = new HeaderBlock();
                blk.op = HeaderBlock.HeaderOp.Index;
                blk.index = idx;
                break;
            }
        }
        if(blk == null) {
            blk = new HeaderBlock();
            if (idxList.size() > 0) {
                blk.op = HeaderBlock.HeaderOp.KnownHeader;
                blk.index = idxList.get(0);
                blk.value = value;
            } else {
                blk.op = HeaderBlock.HeaderOp.UnknownHeader;
                blk.name = name;
                blk.value = value;
            }
        }

        return blk;
    }

    public HeaderBlock buildStatusBlock(int status, HeaderTable tbl) {
        int stIndex = -1;

        ArrayList<Integer> statusIndexList = tbl.get(":status");
        for(int index : statusIndexList) {
            KeyVal kv = tbl.get(index);
            if(kv != null &&  status == Integer.parseInt(kv.value)) {
                stIndex = index;
                break;
            }
        }

        HeaderBlock blk = new HeaderBlock();
        if(stIndex != -1) {
            blk.op = HeaderBlock.HeaderOp.Index;
            blk.index = stIndex;
        }
        else {
            blk.op = HeaderBlock.HeaderOp.KnownHeader;
            blk.index = statusIndexList.get(0);
            blk.value = Integer.toString(status);
        }

        return blk;
    }


}
