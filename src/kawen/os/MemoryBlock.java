package kawen.os;

import kawen.os.PCB;

public class MemoryBlock {
    int start;
    int length;
    boolean isFree;

    public MemoryBlock(int start, int length, boolean isFree) {
        this.start = start;
        this.length = length;
        this.isFree = isFree;
    }
}

