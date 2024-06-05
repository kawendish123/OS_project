package kawen.os;

import java.util.ArrayList;
import java.util.List;

public class PCB {
    String name;
    int PID;
    int burstTime;
    int priority;
    String state;
    int memorySize;
    int memoryStart;
    boolean isIndependent;
    List<PCB> predecessors = new ArrayList<>();//前趋队列
    List<PCB> successors = new ArrayList<>();//后继队列

    public PCB(String name, int PID, int burstTime, int priority, boolean isIndependent, int memorySize) {
        this.name = name;
        this.PID = PID;
        this.burstTime = burstTime;
        this.priority = priority;
        this.isIndependent = isIndependent;
        this.memorySize = memorySize;
        this.state = "Ready";
    }

    @Override
    public String toString() {
        return name + "(PID=" + PID + ")";
    }
}
