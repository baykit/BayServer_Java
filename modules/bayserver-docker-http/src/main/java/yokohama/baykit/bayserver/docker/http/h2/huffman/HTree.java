package yokohama.baykit.bayserver.docker.http.h2.huffman;

import java.io.CharArrayWriter;

public class HTree {

    static HNode root = new HNode();

    public static String decode(byte[] data) {
        CharArrayWriter w = new CharArrayWriter();
        HNode cur = root;
        for(int i = 0; i < data.length; i++) {
            for(int j = 0; j < 8; j++) {
                int bit = data[i] >> (8 - j - 1) & 0x1;                 

                // down tree
                if(bit == 1) {
                    cur = cur.one;
                }
                else {
                    cur = cur.zero;
                }
                
                if(cur.value > 0) {
                    // leaf node
                    w.write(cur.value);
                    cur = root;
                }
            }
        }
        return w.toString();
    }
    
    
    
    static void insert(int code, int lenInBits, int sym) {
        int[] bits = new int[lenInBits];
        for (int i = 0; i < lenInBits; i++) {
            bits[i] = code >> (lenInBits - i - 1) & 0x1;
        }
        insert(bits, sym);
    }

    static void insert(int code[], int sym) {
        HNode curNode = root;
        for(int i = 0; i < code.length; i++) {
            if(code[i] == 1) {
                if(curNode.one == null) {
                    curNode.one = new HNode();
                }
                curNode = curNode.one;
            }
            else {
                if(curNode.zero == null) {
                    curNode.zero = new HNode();
                }
                curNode = curNode.zero;
            }
        }
        curNode.value = sym;
    }
    
    static {
        insert(0x1ff8,13,0);
        insert(0x7fffd8,23,1);
        insert(0xfffffe2,28,2);
        insert(0xfffffe3,28,3);
        insert(0xfffffe4,28,4);
        insert(0xfffffe5,28,5);
        insert(0xfffffe6,28,6);
        insert(0xfffffe7,28,7);
        insert(0xfffffe8,28,8);
        insert(0xffffea,24,9);
        insert(0x3ffffffc,30,10);
        insert(0xfffffe9,28,11);
        insert(0xfffffea,28,12);
        insert(0x3ffffffd,30,13);
        insert(0xfffffeb,28,14);
        insert(0xfffffec,28,15);
        insert(0xfffffed,28,16);
        insert(0xfffffee,28,17);
        insert(0xfffffef,28,18);
        insert(0xffffff0,28,19);
        insert(0xffffff1,28,20);
        insert(0xffffff2,28,21);
        insert(0x3ffffffe,30,22);
        insert(0xffffff3,28,23);
        insert(0xffffff4,28,24);
        insert(0xffffff5,28,25);
        insert(0xffffff6,28,26);
        insert(0xffffff7,28,27);
        insert(0xffffff8,28,28);
        insert(0xffffff9,28,29);
        insert(0xffffffa,28,30);
        insert(0xffffffb,28,31);
        insert(0x14,6,32);
        insert(0x3f8,10,33);
        insert(0x3f9,10,34);
        insert(0xffa,12,35);
        insert(0x1ff9,13,36);
        insert(0x15,6,37);
        insert(0xf8,8,38);
        insert(0x7fa,11,39);
        insert(0x3fa, 10, 40);         
        insert(0x3fb,10,41);
        insert(0xf9,8,42);
        insert(0x7fb,11,43);
        insert(0xfa,8,44);
        insert(0x16,6,45);
        insert(0x17,6,46);
        insert(0x18,6,47);
        insert(0x0,5,48);
        insert(0x1,5,49);
        insert(0x2,5,50);
        insert(0x19,6,51);
        insert(0x1a,6,52);
        insert(0x1b,6,53);
        insert(0x1c,6,54);
        insert(0x1d,6,55);
        insert(0x1e,6,56);
        insert(0x1f,6,57);
        insert(0x5c,7,58);
        insert(0xfb,8,59);
        insert(0x7ffc,15,60);
        insert(0x20,6,61);
        insert(0xffb,12,62);
        insert(0x3fc,10,63);
        insert(0x1ffa,13,64);
        insert(0x21,6,65);
        insert(0x5d,7,66);
        insert(0x5e,7,67);
        insert(0x5f,7,68);
        insert(0x60,7,69);
        insert(0x61,7,70);
        insert(0x62,7,71);
        insert(0x63,7,72);
        insert(0x64,7,73);
        insert(0x65,7,74);
        insert(0x66,7,75);
        insert(0x67,7,76);
        insert(0x68,7,77);
        insert(0x69,7,78);
        insert(0x6a,7,79);
        insert(0x6b,7,80);
        insert(0x6c,7,81);
        insert(0x6d,7,82);
        insert(0x6e,7,83);
        insert(0x6f,7,84);
        insert(0x70,7,85);
        insert(0x71,7,86);
        insert(0x72,7,87);
        insert(0xfc,8,88);
        insert(0x73,7,89);
        insert(0xfd,8,90);
        insert(0x1ffb,13,91);
        insert(0x7fff0,19,92);
        insert(0x1ffc,13,93);
        insert(0x3ffc,14,94);
        insert(0x22,6,95);
        insert(0x7ffd,15,96);
        insert(0x3,5,97);
        insert(0x23,6,98);
        insert(0x4,5,99);
        insert(0x24,6,100);
        insert(0x5,5,101);
        insert(0x25,6,102);
        insert(0x26,6,103);
        insert(0x27,6,104);
        insert(0x6,5,105);
        insert(0x74,7,106);
        insert(0x75,7,107);
        insert(0x28,6,108);
        insert(0x29,6,109);
        insert(0x2a,6,110);
        insert(0x7,5,111);
        insert(0x2b,6,112);
        insert(0x76,7,113);
        insert(0x2c,6,114);
        insert(0x8,5,115);
        insert(0x9,5,116);
        insert(0x2d,6,117);
        insert(0x77,7,118);
        insert(0x78,7,119);
        insert(0x79,7,120);
        insert(0x7a,7,121);
        insert(0x7b,7,122);
        insert(0x7ffe,15,123);
        insert(0x7fc,11,124);
        insert(0x3ffd,14,125);
        insert(0x1ffd,13,126);
        insert(0xffffffc,28,127);
        insert(0xfffe6,20,128);
        insert(0x3fffd2,22,129);
        insert(0xfffe7,20,130);
        insert(0xfffe8,20,131);
        insert(0x3fffd3,22,132);
        insert(0x3fffd4,22,133);
        insert(0x3fffd5,22,134);
        insert(0x7fffd9,23,135);
        insert(0x3fffd6,22,136);
        insert(0x7fffda,23,137);
        insert(0x7fffdb,23,138);
        insert(0x7fffdc,23,139);
        insert(0x7fffdd,23,140);
        insert(0x7fffde,23,141);
        insert(0xffffeb,24,142);
        insert(0x7fffdf,23,143);
        insert(0xffffec,24,144);
        insert(0xffffed,24,145);
        insert(0x3fffd7,22,146);
        insert(0x7fffe0,23,147);
        insert(0xffffee,24,148);
        insert(0x7fffe1,23,149);
        insert(0x7fffe2,23,150);
        insert(0x7fffe3,23,151);
        insert(0x7fffe4,23,152);
        insert(0x1fffdc,21,153);
        insert(0x3fffd8,22,154);
        insert(0x7fffe5,23,155);
        insert(0x3fffd9,22,156);
        insert(0x7fffe6,23,157);
        insert(0x7fffe7,23,158);
        insert(0xffffef,24,159);
        insert(0x3fffda,22,160);
        insert(0x1fffdd,21,161);
        insert(0xfffe9,20,162);
        insert(0x3fffdb,22,163);
        insert(0x3fffdc,22,164);
        insert(0x7fffe8,23,165);
        insert(0x7fffe9,23,166);
        insert(0x1fffde,21,167);
        insert(0x7fffea,23,168);
        insert(0x3fffdd,22,169);
        insert(0x3fffde,22,170);
        insert(0xfffff0,24,171);
        insert(0x1fffdf,21,172);
        insert(0x3fffdf,22,173);
        insert(0x7fffeb,23,174);
        insert(0x7fffec,23,175);
        insert(0x1fffe0,21,176);
        insert(0x1fffe1,21,177);
        insert(0x3fffe0,22,178);
        insert(0x1fffe2,21,179);
        insert(0x7fffed,23,180);
        insert(0x3fffe1,22,181);
        insert(0x7fffee,23,182);
        insert(0x7fffef,23,183);
        insert(0xfffea,20,184);
        insert(0x3fffe2,22,185);
        insert(0x3fffe3,22,186);
        insert(0x3fffe4,22,187);
        insert(0x7ffff0,23,188);
        insert(0x3fffe5,22,189);
        insert(0x3fffe6,22,190);
        insert(0x7ffff1,23,191);
        insert(0x3ffffe0,26,192);
        insert(0x3ffffe1,26,193);
        insert(0xfffeb,20,194);
        insert(0x7fff1,19,195);
        insert(0x3fffe7,22,196);
        insert(0x7ffff2,23,197);
        insert(0x3fffe8,22,198);
        insert(0x1ffffec,25,199);
        insert(0x3ffffe2,26,200);
        insert(0x3ffffe3,26,201);
        insert(0x3ffffe4,26,202);
        insert(0x7ffffde,27,203);
        insert(0x7ffffdf,27,204);
        insert(0x3ffffe5,26,205);
        insert(0xfffff1,24,206);
        insert(0x1ffffed,25,207);
        insert(0x7fff2,19,208);
        insert(0x1fffe3,21,209);
        insert(0x3ffffe6,26,210);
        insert(0x7ffffe0,27,211);
        insert(0x7ffffe1,27,212);
        insert(0x3ffffe7,26,213);
        insert(0x7ffffe2,27,214);
        insert(0xfffff2,24,215);
        insert(0x1fffe4,21,216);
        insert(0x1fffe5,21,217);
        insert(0x3ffffe8,26,218);
        insert(0x3ffffe9,26,219);
        insert(0xffffffd,28,220);
        insert(0x7ffffe3,27,221);
        insert(0x7ffffe4,27,222);
        insert(0x7ffffe5,27,223);
        insert(0xfffec,20,224);
        insert(0xfffff3,24,225);
        insert(0xfffed,20,226);
        insert(0x1fffe6,21,227);
        insert(0x3fffe9,22,228);
        insert(0x1fffe7,21,229);
        insert(0x1fffe8,21,230);
        insert(0x7ffff3,23,231);
        insert(0x3fffea,22,232);
        insert(0x3fffeb,22,233);
        insert(0x1ffffee,25,234);
        insert(0x1ffffef,25,235);
        insert(0xfffff4,24,236);
        insert(0xfffff5,24,237);
        insert(0x3ffffea,26,238);
        insert(0x7ffff4,23,239);
        insert(0x3ffffeb,26,240);
        insert(0x7ffffe6,27,241);
        insert(0x3ffffec,26,242);
        insert(0x3ffffed,26,243);
        insert(0x7ffffe7,27,244);
        insert(0x7ffffe8,27,245);
        insert(0x7ffffe9,27,246);
        insert(0x7ffffea,27,247);
        insert(0x7ffffeb,27,248);
        insert(0xffffffe,28,249);
        insert(0x7ffffec,27,250);
        insert(0x7ffffed,27,251);
        insert(0x7ffffee,27,252);
        insert(0x7ffffef,27,253);
        insert(0x7fffff0,27,254);
        insert(0x3ffffee,26,255);
        insert(0x3fffffff,30,256);
    }
}