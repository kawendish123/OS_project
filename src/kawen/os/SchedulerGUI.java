package kawen.os;

import kawen.os.MemoryBlock;
import kawen.os.PCB;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SchedulerGUI {
    static List<PCB> readyQueue = new ArrayList<>();
    static List<PCB> backupQueue = new ArrayList<>();
    static List<PCB> suspendedQueue = new ArrayList<>();
    static List<PCB> finishedQueue=new ArrayList<>();
    static List<MemoryBlock> memoryBlocks = new ArrayList<>();
    static int memorySize = 1000;
    static int osMemory = 100;
    static int nextPID = 1;
    static final int TIME_SLICE = 1;
    static final int MAX_CONCURRENT_PROCESSES = 2;
    static final int MIN_PRIORITY = 0;

    static DefaultTableModel processTableModel;
    static DefaultTableModel memoryTableModel;
    static JTextArea schedulingLog;
    static JButton continueButton;
    static boolean continueFlag = false;

    static {
        memoryBlocks.add(new MemoryBlock(osMemory, memorySize - osMemory, true));
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("处理机调度及内存分配及回收机制");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTabbedPane tabbedPane = new JTabbedPane();

        // Process Management Tab
        JPanel processPanel = new JPanel(new BorderLayout());
        processTableModel = new DefaultTableModel(new String[]{"PID", "进程名", "运行时间", "优先权", "状态", "主存起始位置", "所需主存大小"}, 0);
        JTable processTable = new JTable(processTableModel);
        processPanel.add(new JScrollPane(processTable), BorderLayout.CENTER);

        JPanel processControlPanel = new JPanel();
        JButton addProcessButton = new JButton("添加进程");
        JButton runSchedulingButton = new JButton("进程调度");
        JButton suspendProcessButton = new JButton("进程挂起");
        JButton resumeProcessButton = new JButton("进程解挂");
        continueButton = new JButton("继续调度");
        continueButton.setEnabled(false);

        processControlPanel.add(addProcessButton);
        processControlPanel.add(runSchedulingButton);
        processControlPanel.add(suspendProcessButton);
        processControlPanel.add(resumeProcessButton);
        processControlPanel.add(continueButton);
        processPanel.add(processControlPanel, BorderLayout.SOUTH);

        tabbedPane.add("进程", processPanel);

        // Memory Management Tab
        JPanel memoryPanel = new JPanel(new BorderLayout());
        memoryTableModel = new DefaultTableModel(new String[]{"起址", "长度", "状态"}, 0);
        JTable memoryTable = new JTable(memoryTableModel);
        memoryPanel.add(new JScrollPane(memoryTable), BorderLayout.CENTER);

        tabbedPane.add("主存", memoryPanel);

        // Scheduling Log Tab
        JPanel logPanel = new JPanel(new BorderLayout());
        schedulingLog = new JTextArea();
        schedulingLog.setEditable(false);
        logPanel.add(new JScrollPane(schedulingLog), BorderLayout.CENTER);

        tabbedPane.add("调度日志", logPanel);

        frame.add(tabbedPane);
        frame.setVisible(true);

        addProcessButton.addActionListener(e -> addProcess());
        runSchedulingButton.addActionListener(e -> runScheduling());
        suspendProcessButton.addActionListener(e -> suspendProcess());
        resumeProcessButton.addActionListener(e -> resumeProcess());
        continueButton.addActionListener(e -> continueScheduling());

        updateMemoryTable();
    }

    static void addProcess() {
        JTextField nameField = new JTextField();
        JTextField burstTimeField = new JTextField();
        JTextField priorityField = new JTextField();
        JTextField memorySizeField = new JTextField();
        JCheckBox isIndependentBox = new JCheckBox("独立进程");

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("进程名:"));
        panel.add(nameField);
        panel.add(new JLabel("运行时间:"));
        panel.add(burstTimeField);
        panel.add(new JLabel("优先权:"));
        panel.add(priorityField);
        panel.add(new JLabel("主存大小:"));
        panel.add(memorySizeField);
        panel.add(isIndependentBox);

        int result = JOptionPane.showConfirmDialog(null, panel, "添加进程", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText();
            int burstTime = Integer.parseInt(burstTimeField.getText());
            int priority = Integer.parseInt(priorityField.getText());
            int memorySize = Integer.parseInt(memorySizeField.getText());
            boolean isIndependent = isIndependentBox.isSelected();

            PCB process = new PCB(name, nextPID++, burstTime, priority, isIndependent, memorySize);

            if (!isIndependent) {
                String predCountStr = JOptionPane.showInputDialog("输入前趋进程的数量:");
                int predCount = Integer.parseInt(predCountStr);
                for (int i = 0; i < predCount; i++) {
                    String predPIDStr = JOptionPane.showInputDialog("输入第" + i + 1 + "前驱进程号:");
                    int predPID = Integer.parseInt(predPIDStr);
                    PCB predecessor = findProcessByPID(predPID);
                    if (predecessor != null) {
                        process.predecessors.add(predecessor);
                        predecessor.successors.add(process);
                    } else {
                        while(predecessor==null){
                            JOptionPane.showMessageDialog(null, "未找到前趋进程PID。请重新输入.");
                            predPIDStr = JOptionPane.showInputDialog("输入第" + i + 1 + "前驱进程号:");
                            predPID = Integer.parseInt(predPIDStr);
                            predecessor = findProcessByPID(predPID);
                        }
                        process.predecessors.add(predecessor);
                        predecessor.successors.add(process);
                    }
                }
            }

            int memoryStart = allocateMemory(memorySize);
            if (memoryStart == -1) {
                JOptionPane.showMessageDialog(null, "\n" +
                        "内存不足。进程已添加到后备队列。");
                backupQueue.add(process);
                updateProcessTable();
            } else {
                process.memoryStart = memoryStart;
                readyQueue.add(process);
                updateProcessTable();
            }
        }
    }




    static PCB findProcessByPID(int pid) {
        for (PCB process : readyQueue) {
            if (process.PID == pid) return process;
        }
        for (PCB process : backupQueue) {
            if (process.PID == pid) return process;
        }
        for (PCB process : suspendedQueue) {
            if (process.PID == pid) return process;
        }
        return null;
    }

    static void runScheduling() {
        new Thread(() -> {
            while (!readyQueue.isEmpty() || !backupQueue.isEmpty()) {
                //内存中进程是否＜规定道数
                while (readyQueue.size() < MAX_CONCURRENT_PROCESSES && !backupQueue.isEmpty()) {
                    PCB process = backupQueue.remove(0);//从后备进程调入作业
                    int memoryStart = allocateMemory(process.memorySize);
                    //判断主存中是否够分配空间
                    if (memoryStart != -1) {
                        process.memoryStart = memoryStart;
                        process.state = "Ready";
                        readyQueue.add(process);
                        updateProcessTable();
                    } else {
                        process.state="Backup";
                        backupQueue.add(process);
                        break;
                    }
                }

                //按照优先权从大到小排列
                readyQueue.sort(Comparator.comparingInt((PCB p) -> p.priority).reversed());

                PCB[] runningProcesses = new PCB[MAX_CONCURRENT_PROCESSES];
                boolean[] processorAssigned = new boolean[readyQueue.size()];

                //处理机分配进程
                for (int i = 0; i < MAX_CONCURRENT_PROCESSES; i++) {
                    PCB process = selectProcessForProcessor(i, processorAssigned);
                    if (process != null) {
                        process.state = "Running";
                        runningProcesses[i] = process;
                        process.burstTime -= TIME_SLICE;
                        process.priority = Math.max(MIN_PRIORITY, process.priority - 1);

                        schedulingLog.append("处理机 " + (i + 1) + " 调度进程: " + process + "\n");

                        if (process.burstTime <= 0) {
                            process.state = "Finished";
                            finishedQueue.add(process);
                            readyQueue.remove(process);
                            releaseMemory(process);
                            //检查后继进程是否能调出阻塞队列
                            for (PCB successor : process.successors) {
                                successor.predecessors.remove(process);
                                if (successor.predecessors.isEmpty()) {
                                    successor.state = "Ready";
                                } else {
                                    successor.state = "Blocked";
                                }
                            }
                        }
                    }
                }
                //调度结束后再按优先权排序，实现抢占式
                readyQueue.sort(Comparator.comparingInt((PCB p) -> p.priority).reversed());
                //更新界面
                updateProcessTable();
                for (PCB process : readyQueue){
                    if (process.state=="Running") process.state= "Ready";
                }
                updateMemoryTable();
                continueButton.setEnabled(true);
                while (!continueFlag) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                continueFlag = false;
                continueButton.setEnabled(false);
            }
        }).start();
    }



    static PCB selectProcessForProcessor(int processorIndex, boolean[] processorAssigned) {
        for (int i = 0; i < readyQueue.size(); i++) {
            PCB process = readyQueue.get(i);
            if (!processorAssigned[i] && process.state.equals("Ready")) {
                if (process.isIndependent || process.predecessors.stream().allMatch(p -> p.state.equals("Finished"))) {
                    processorAssigned[i] = true;
                    return process;
                } else {
                    process.state = "Blocked";
                }
            }
        }
        return null;
    }



    static void continueScheduling() {
        continueFlag = true;
    }

    static void suspendProcess() {
        String pidStr = JOptionPane.showInputDialog("输入要挂起进程的PID:");
        if (pidStr != null) {
            int pid = Integer.parseInt(pidStr);
            PCB process = findProcessByPID(pid);
            if (process != null && process.state.equals("Ready")) {
                process.state = "Suspended";
                readyQueue.remove(process);
                suspendedQueue.add(process);
                updateProcessTable();
            } else {
                JOptionPane.showMessageDialog(null, "该进程不可被挂起");
            }
        }
    }

    static void resumeProcess() {
        String pidStr = JOptionPane.showInputDialog("输入解挂进程的PID:");
        if (pidStr != null) {
            int pid = Integer.parseInt(pidStr);
            PCB process = findProcessByPID(pid);
            if (process != null && process.state.equals("Suspended")) {
                process.state = "Ready";
                suspendedQueue.remove(process);
                readyQueue.add(process);
                updateProcessTable();
            } else {
                JOptionPane.showMessageDialog(null, "该进程不在挂起队列中");
            }
        }
    }

    static int allocateMemory(int size) {
        for (MemoryBlock block : memoryBlocks) {
            if (block.isFree && block.length >= size) {
                int start = block.start;
                if (block.length == size) {
                    block.isFree = false;
                } else {
                    block.start += size;
                    block.length -= size;
                    memoryBlocks.add(new MemoryBlock(start, size, false));
                }
                updateMemoryTable();
                return start;
            }
        }
        return -1;
    }

    static void releaseMemory(PCB process) {
        for (MemoryBlock block : memoryBlocks) {
            if (!block.isFree && block.start == process.memoryStart && block.length == process.memorySize) {
                block.isFree = true;
                mergeFreeBlocks();
                updateMemoryTable();
                return;
            }
        }
    }

    static void mergeFreeBlocks() {
        Collections.sort(memoryBlocks, Comparator.comparingInt(b -> b.start));
        List<MemoryBlock> mergedBlocks = new ArrayList<>();
        MemoryBlock previous = null;

        for (MemoryBlock block : memoryBlocks) {
            if (block.isFree) {
                if (previous == null) {
                    previous = block;
                } else if (previous.start + previous.length == block.start) {
                    previous.length += block.length;
                } else {
                    mergedBlocks.add(previous);
                    previous = block;
                }
            } else {
                if (previous != null) {
                    mergedBlocks.add(previous);
                    previous = null;
                }
                mergedBlocks.add(block);
            }
        }

        if (previous != null) {
            mergedBlocks.add(previous);
        }

        memoryBlocks = mergedBlocks;
    }

    static void updateProcessTable() {
        processTableModel.setRowCount(0);
        for (PCB process : readyQueue) {
            processTableModel.addRow(new Object[]{process.PID, process.name, process.burstTime, process.priority, process.state, process.memoryStart, process.memorySize});
        }
        for (PCB process : backupQueue) {
            processTableModel.addRow(new Object[]{process.PID, process.name, process.burstTime, process.priority, "Backup", process.memoryStart, process.memorySize});
        }
        for (PCB process : suspendedQueue) {
            processTableModel.addRow(new Object[]{process.PID, process.name, process.burstTime, process.priority, process.state, process.memoryStart, process.memorySize});
        }
        for (PCB process : finishedQueue) {
            processTableModel.addRow(new Object[]{process.PID, process.name, process.burstTime, process.priority, process.state, process.memoryStart, process.memorySize});
        }
    }

    static void updateMemoryTable() {
        memoryTableModel.setRowCount(0);
        for (MemoryBlock block : memoryBlocks) {
            memoryTableModel.addRow(new Object[]{block.start, block.length, block.isFree});
        }
    }
}