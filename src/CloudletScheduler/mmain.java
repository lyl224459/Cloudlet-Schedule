package CloudletScheduler;

import CloudletScheduler.datacenter.Constants;
import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.datacenter.Type;
import CloudletScheduler.Optimizer.dbo.DBOScheduler;
import CloudletScheduler.Optimizer.random.RandomScheduler;
import CloudletScheduler.Optimizer.woa.WOAScheduler;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * 多调度器对比实验：每个算法的结果保存到独立的 CSV 文件中
 */
public class mmain {

    private static final int CLOUDLET_N = 1000;
    private static final int NUM_USER = 1;
    private static final int REPEAT_TIMES = 10;
    private static final String RESULT_DIR = "results";

    public static final List<SchedulerFactory> SCHEDULERS = Arrays.asList(
            new SchedulerFactory("WOA", WOAScheduler::new),
            new SchedulerFactory("DBO", DBOScheduler::new),
            new SchedulerFactory("Random", RandomScheduler::new)
    );

    public static void main(String[] args) throws Exception {
        // 创建 results 目录
        Files.createDirectories(Paths.get(RESULT_DIR));

        Log.printLine("Starting multi-scheduler comparison with " + REPEAT_TIMES + " repetitions...");
        Log.printLine("Results will be saved as CSV files in: " + RESULT_DIR + "/");

        // 初始化：每个调度器对应一个 CSV 写入器和累加器
        Map<String, PrintWriter> writers = new LinkedHashMap<>();
        Map<String, Accumulator> accumulators = new LinkedHashMap<>();

        try {
            // 打开所有 CSV 文件并写入表头
            for (SchedulerFactory factory : SCHEDULERS) {
                String name = factory.name;
                PrintWriter writer = new PrintWriter(new FileWriter(RESULT_DIR + "/" + name + ".csv"));
                writer.println("Run,Makespan,LoadBalance,Cost");
                writers.put(name, writer);
                accumulators.put(name, new Accumulator());
            }

            // 开始重复实验
            for (int run = 1; run <= REPEAT_TIMES; run++) {
                Log.printLine("\n--- Run " + run + " / " + REPEAT_TIMES + " ---");
                for (SchedulerFactory factory : SCHEDULERS) {
                    Log.printLine("Running " + factory.name + " ...");
                    SimulationResult result = runSimulation(factory.creator, run);

                    // 写入当前运行结果到对应 CSV
                    PrintWriter w = writers.get(factory.name);
                    w.printf("%d,%.6f,%.6f,%.6f%n", run, result.makespan, result.loadBalance, result.cost);
                    w.flush(); // 立即写入磁盘

                    // 累加用于计算平均值
                    accumulators.get(factory.name).add(result);
                }
            }

            // 打印平均结果到控制台
            System.out.println("\n" + "=".repeat(60));
            System.out.println("📊 AVERAGE RESULTS OVER " + REPEAT_TIMES + " RUNS");
            System.out.println("=".repeat(60));
            System.out.printf("%-12s | %10s | %10s | %12s%n", "Scheduler", "Makespan", "LoadBal", "Cost");
            System.out.println("-".repeat(60));
            for (SchedulerFactory factory : SCHEDULERS) {
                Accumulator acc = accumulators.get(factory.name);
                System.out.printf("%-12s | %10.2f | %10.2f | %12.2f%n",
                        factory.name,
                        acc.totalMakespan / REPEAT_TIMES,
                        acc.totalLoadBalance / REPEAT_TIMES,
                        acc.totalCost / REPEAT_TIMES);
            }
            System.out.println("=".repeat(60));

        } finally {
            // 安全关闭所有文件流
            for (PrintWriter w : writers.values()) {
                if (w != null) {
                    try {
                        w.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        Log.printLine("\n✅ All results saved successfully in '" + RESULT_DIR + "' directory.");
    }

    /**
     * 运行单次模拟
     */
    private static SimulationResult runSimulation(SchedulerCreator creator, int runIndex) throws Exception {
        Random rand = new Random(runIndex);
        CloudSim.init(NUM_USER, Calendar.getInstance(), false);

        Datacenter dc0 = createDatacenter("DC0", Type.LOW, rand);
        Datacenter dc1 = createDatacenter("DC1", Type.MEDIUM, rand);
        Datacenter dc2 = createDatacenter("DC2", Type.HIGH, rand);

        DatacenterBroker broker = new DatacenterBroker("Broker");
        int brokerId = broker.getId();

        List<Vm> vmList = createVms(brokerId, rand);
        List<Cloudlet> cloudletList = createCloudlets(brokerId, rand);

        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        Scheduler scheduler = creator.create(cloudletList, vmList);
        scheduler.schedule();

        CloudSim.startSimulation();

        List<Cloudlet> finishedCloudlets = broker.getCloudletReceivedList();
        return analyzeResults(finishedCloudlets, vmList.size());
    }

    // ================== 辅助函数与类 ==================

    @FunctionalInterface
    interface SchedulerCreator {
        Scheduler create(List<Cloudlet> cloudlets, List<Vm> vms);
    }

    static class SchedulerFactory {
        String name;
        SchedulerCreator creator;

        SchedulerFactory(String name, SchedulerCreator creator) {
            this.name = name;
            this.creator = creator;
        }
    }

    static class SimulationResult {
        double makespan;
        double loadBalance;
        double cost;

        SimulationResult(double makespan, double loadBalance, double cost) {
            this.makespan = makespan;
            this.loadBalance = loadBalance;
            this.cost = cost;
        }
    }

    static class Accumulator {
        double totalMakespan = 0;
        double totalLoadBalance = 0;
        double totalCost = 0;

        void add(SimulationResult r) {
            totalMakespan += r.makespan;
            totalLoadBalance += r.loadBalance;
            totalCost += r.cost;
        }
    }

    // ------------------ 原有数据生成方法（保持不变）------------------

    private static Datacenter createDatacenter(String name, Type type, Random rand) throws Exception {
        int ram, bw, mips;
        long storage;
        double costPerSec;

        switch (type) {
            case LOW:
                ram = Constants.RAM * Constants.L_VM_N;
                bw = Constants.BW * Constants.L_VM_N;
                mips = Constants.L_MIPS * Constants.L_VM_N;
                storage = Constants.STORAGE * Constants.L_VM_N;
                costPerSec = Constants.L_PRICE;
                break;
            case MEDIUM:
                ram = Constants.RAM * Constants.M_VM_N;
                bw = Constants.BW * Constants.M_VM_N;
                mips = Constants.M_MIPS * Constants.M_VM_N;
                storage = Constants.STORAGE * Constants.M_VM_N;
                costPerSec = Constants.M_PRICE;
                break;
            case HIGH:
                ram = Constants.RAM * Constants.H_VM_N;
                bw = Constants.BW * Constants.H_VM_N;
                mips = Constants.H_MIPS * Constants.H_VM_N;
                storage = Constants.STORAGE * Constants.H_VM_N;
                costPerSec = Constants.H_PRICE;
                break;
            default:
                throw new Exception("Invalid datacenter type");
        }

        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerSimple(mips)));

        List<Host> hostList = new ArrayList<>();
        hostList.add(new Host(
                0,
                new RamProvisionerSimple(ram),
                new BwProvisionerSimple(bw),
                storage,
                peList,
                new VmSchedulerTimeShared(peList)
        ));

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double timeZone = 10.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.1;

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, timeZone, costPerSec, costPerMem, costPerStorage, costPerBw
        );

        return new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), new LinkedList<>(), 0);
    }

    private static List<Vm> createVms(int userId, Random rand) {
        List<Vm> vmList = new ArrayList<>();
        int vmId = 0;
        int pesNumber = 1;
        String vmm = "Xen";

        for (int i = 0; i < Constants.L_VM_N; i++) {
            vmList.add(new Vm(vmId++, userId, Constants.L_MIPS, pesNumber, Constants.RAM, Constants.BW, Constants.IMAGE_SIZE, vmm, new CloudletSchedulerSpaceShared()));
        }
        for (int i = 0; i < Constants.M_VM_N; i++) {
            vmList.add(new Vm(vmId++, userId, Constants.M_MIPS, pesNumber, Constants.RAM, Constants.BW, Constants.IMAGE_SIZE, vmm, new CloudletSchedulerSpaceShared()));
        }
        for (int i = 0; i < Constants.H_VM_N; i++) {
            vmList.add(new Vm(vmId++, userId, Constants.H_MIPS, pesNumber, Constants.RAM, Constants.BW, Constants.IMAGE_SIZE, vmm, new CloudletSchedulerSpaceShared()));
        }
        return vmList;
    }

    private static List<Cloudlet> createCloudlets(int userId, Random rand) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        int id = 0;
        int pesNumber = 1;
        UtilizationModel utilizationModel = new UtilizationModelFull();

        double mean = 30000.0;
        double variance = 1.5;
        for (int i = 0; i < CLOUDLET_N; i++) {
            long length = (long) (Math.exp(rand.nextGaussian() * variance + Math.log(mean)));
            long fileSize = Math.max(10, (long) (rand.nextGaussian() * 100 + 100));
            long outputSize = Math.max(10, (long) (rand.nextGaussian() * 100 + 100));

            Cloudlet cloudlet = new Cloudlet(id++, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
            cloudlet.setUserId(userId);
            cloudletList.add(cloudlet);
        }
        return cloudletList;
    }

    private static SimulationResult analyzeResults(List<Cloudlet> cloudletList, int vmCount) {
        double makespan = 0;
        double[] executeTimeOfVM = new double[vmCount];
        double totalCost = 0;

        for (Cloudlet cloudlet : cloudletList) {
            if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
                double finishTime = cloudlet.getFinishTime();
                makespan = Math.max(makespan, finishTime);

                int vmId = cloudlet.getVmId();
                double actualCPUTime = cloudlet.getActualCPUTime();
                executeTimeOfVM[vmId] += actualCPUTime;
                totalCost += actualCPUTime * cloudlet.getCostPerSec();
            }
        }

        double avg = Arrays.stream(executeTimeOfVM).average().orElse(0.0);
        double lb = Math.sqrt(Arrays.stream(executeTimeOfVM).map(x -> Math.pow(x - avg, 2)).average().orElse(0.0));

        return new SimulationResult(makespan, lb, totalCost);
    }
}