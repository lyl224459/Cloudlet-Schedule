package CloudletScheduler;

import CloudletScheduler.datacenter.Constants;
import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.datacenter.Type;
import CloudletScheduler.Optimizer.woa.WOAScheduler;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.text.DecimalFormat;
import java.util.*;

/**
 * @author : LA4AM12
 * @create : 2023-02-23 09:33:04
 * @description : Cloud Task (Cloudlet) Scheduling Simulation
 */
public class Main {

    /**
     * Cloudlets count
     */
    private static final int CLOUDLET_N = 1000;

    private static final Random R = new Random(0);

    private static final int NUM_USER = 1;

    /**
     * 程序的主入口点
     * 初始化云模拟环境，并设置数据中心、虚拟机和云任务
     *
     * @param args 命令行参数
     * @throws Exception 如果模拟过程中发生错误
     */
    public static void main(String[] args) throws Exception {
        // 打印启动信息
        Log.printLine("Starting...");

        // 初始化CloudSim库，设置用户数量、日历和是否启用日志记录
        CloudSim.init(NUM_USER, Calendar.getInstance(), true);

        // 创建三个不同级别的数据中心
        Datacenter datacenter0 = createDatacenter("Datacenter0", Type.LOW);
        Datacenter datacenter1 = createDatacenter("Datacenter1", Type.MEDIUM);
        Datacenter datacenter2 = createDatacenter("Datacenter2", Type.HIGH);

        // 创建一个数据中经纪人
        DatacenterBroker broker = new DatacenterBroker("Broker");
        int brokerId = broker.getId();

        // 创建虚拟机列表并提交给经纪人
        List<Vm> vmList = createVms(brokerId);
        broker.submitVmList(vmList);

        // 创建云任务列表并提交给经纪人
        List<Cloudlet> cloudletList = createCloudlets(brokerId);
        broker.submitCloudletList(cloudletList);

        // 选择并初始化调度器
        // 可以选择不同的调度算法，如随机调度、MinMin、MaxMin、鲸鱼优化算法、遗传算法等
        // 这里选择了一种假设的高性能遗传算法调度器
        Scheduler scheduler = new WOAScheduler(cloudletList, vmList);
//        Scheduler scheduler = new DBOScheduler(cloudletList,vmList);
        // 执行调度
        scheduler.schedule();

        // 打印开始模拟的信息
        Log.printLine("========== START ==========");
        // 开始CloudSim模拟
        CloudSim.startSimulation();

        // 获取并打印完成的云任务列表
        List<Cloudlet> newList = broker.getCloudletReceivedList();
        printCloudletList(newList);
    }

    /**
     * 根据指定的类型创建并配置数据中心。
     *
     * @param name 数据中心的名称。
     * @param type 数据中心的类型，决定了其配置。
     * @return 返回创建的数据中心对象。
     * @throws Exception 如果提供了无效的数据中心类型，则抛出异常。
     */
    private static Datacenter createDatacenter(String name, Type type) throws Exception {
        int ram, bw, mips;
        long storage;
        double costPerSec;

        // 根据数据中心的类型确定其配置。
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
                // 如果数据中心类型无效，抛出异常。
                throw new Exception("无效的数据中心类型");
        }

        // 初始化主机列表和处理元素列表。
        List<Host> hostList = new ArrayList<>();
        List<Pe> peList = new ArrayList<>();

        // 添加一个具有指定计算能力的处理元素。
        peList.add(new Pe(0, new PeProvisionerSimple(mips)));

        // 添加一个具有指定资源和时间共享虚拟机调度器的主机。
        hostList.add(
                new Host(
                        0,
                        new RamProvisionerSimple(ram),
                        new BwProvisionerSimple(bw),
                        storage,
                        peList,
                        new VmSchedulerTimeShared(peList)
                )
        );

        // 定义数据中心的架构、操作系统、虚拟机监控程序、时区和成本参数。
        String arch = "x86"; // 系统架构
        String os = "Linux"; // 操作系统
        String vmm = "Xen";
        double time_zone = 10.0; // 资源所在时区
        double costPerMem = 0.05; // 使用内存的成本
        double costPerStorage = 0.001; // 使用存储的成本
        double costPerGB = 0.1; // 使用带宽的成本
        LinkedList<Storage> storageList = new LinkedList<>(); // 当前不添加SAN设备

        // 创建并返回数据中心对象。
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, costPerSec, costPerMem,
                costPerStorage, costPerGB);

        return new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
    }

    /**
     * 创建并返回一个包含多个虚拟机（Vm）的列表
     * 该方法根据不同的虚拟机配置（如MIPS、RAM等），为用户创建指定数量的虚拟机
     *
     * @param userId 用户ID，用于标识虚拟机属于哪个用户
     * @return 包含创建的虚拟机的列表
     */
    private static List<Vm> createVms(int userId) {
        List<Vm> vmList = new ArrayList<>();

        int vmId = 0;
        int pesNumber = 1; // number of cpus
        String vmm = "Xen"; // VMM name

        // 创建低配置虚拟机
        for (int i = 0; i < Constants.L_VM_N; i++) {
            vmList.add(new Vm(vmId++, userId, Constants.L_MIPS, pesNumber, Constants.RAM, Constants.BW, Constants.IMAGE_SIZE, vmm, new CloudletSchedulerSpaceShared()));
        }

        // 创建中配置虚拟机
        for (int i = 0; i < Constants.M_VM_N; i++) {
            vmList.add(new Vm(vmId++, userId, Constants.M_MIPS, pesNumber, Constants.RAM, Constants.BW, Constants.IMAGE_SIZE, vmm, new CloudletSchedulerSpaceShared()));
        }

        // 创建高配置虚拟机
        for (int i = 0; i < Constants.H_VM_N; i++) {
            vmList.add(new Vm(vmId++, userId, Constants.H_MIPS, pesNumber, Constants.RAM, Constants.BW, Constants.IMAGE_SIZE, vmm, new CloudletSchedulerSpaceShared()));
        }
        return vmList;
    }

    /**
     * 创建云任务列表
     *
     * @param userId 用户ID，用于设置每个云任务的用户ID
     * @return 返回一个包含多个云任务的列表
     */
//    private static List<Cloudlet> createCloudlets(int userId) {
//        // 初始化云任务列表
//        List<Cloudlet> cloudletList = new ArrayList<>();
//        // 初始化云任务ID为0
//        int id = 0;
//        // 设置每个云任务使用的PE（处理元素）数量为1
//        int pesNumber = 1;
//        // 创建一个完全利用率模型实例，表示云任务将一直占用全部资源
//        UtilizationModel utilizationModel = new UtilizationModelFull();
//
//        // 循环创建CLOUDLET_N个云任务
//        for (int i = 0; i < CLOUDLET_N; i++) {
//            // 随机生成云任务的长度（执行时间），范围在10000到50000之间
//            long length = R.nextInt(40000) + 10000;
//            // 随机生成云任务的输入文件大小，范围在10到200之间
//            long fileSize = R.nextInt(190) + 10;
//            // 随机生成云任务的输出文件大小，范围在10到200之间
//            long outputSize = R.nextInt(190) + 10;
//            // 创建一个云任务实例
//            Cloudlet cloudlet = new Cloudlet(id, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
//            // 设置云任务的用户ID
//            cloudlet.setUserId(userId);
//            // 将云任务添加到列表中
//            cloudletList.add(cloudlet);
//            // 增加云任务ID，确保每个云任务有一个唯一的ID
//            id++;
//        }
//        // 返回云任务列表
//        return cloudletList;
//    }
    // 创建云任务列表（改进后的任务生成方法）
    private static List<Cloudlet> createCloudlets(int userId) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        int id = 0;
        int pesNumber = 1;
        UtilizationModel utilizationModel = new UtilizationModelFull();

        // 使用对数正态分布生成执行时间（length），假设任务的执行时间大多集中在某个值周围
        double mean = 30000.0; // 平均执行时间
        double variance = 1.5; // 方差
        for (int i = 0; i < CLOUDLET_N; i++) {
            long length = (long) (Math.exp(R.nextGaussian() * variance + Math.log(mean)));
            // 输入文件大小和输出文件大小也使用正态分布模拟
            long fileSize = Math.max(10, (long) (R.nextGaussian() * 100 + 100));  // 输入文件大小
            long outputSize = Math.max(10, (long) (R.nextGaussian() * 100 + 100));  // 输出文件大小

            // 使用对数正态分布模拟任务执行时间
            Cloudlet cloudlet = new Cloudlet(id, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
            cloudlet.setUserId(userId);
            cloudletList.add(cloudlet);
            id++;
        }
        return cloudletList;
    }

    /**
     * 打印云任务列表信息
     * 该方法遍历云任务列表，并以格式化的方式输出每个云任务的详细信息，包括云任务ID、状态、数据中心ID、VM ID、执行时间等
     * 同时，计算并输出所有任务的总耗时（makespan）、负载均衡度（LB）和总成本（cost）
     *
     * @param cloudletList 云任务列表，包含多个Cloudlet对象
     */
    private static void printCloudletList(List<Cloudlet> cloudletList) {
        // 定义缩进字符串，用于格式化输出
        String indent = "    ";

        // 打印输出云任务信息的标题行
        Log.printLine();
        Log.printLine("========== OUTPUT ==========");
        Log.printLine("Cloudlet ID" + indent + "STATUS" + indent
                + "Data center ID" + indent + "VM ID" + indent + "Time" + indent
                + "Start Time" + indent + "Finish Time" + indent + "BWCost" + indent + "CPUCost");

        // 创建DecimalFormat对象，用于格式化浮点数输出
        DecimalFormat dft = new DecimalFormat("###.##");

        // 初始化总耗时（makespan）、虚拟机数量（vmNum）、每个虚拟机的执行时间（executeTimeOfVM）、总成本（cost）和负载均衡度（LB）
        double makespan = 0;
        int vmNum = Constants.L_VM_N + Constants.M_VM_N + Constants.H_VM_N;
        double[] executeTimeOfVM = new double[vmNum];
        double cost = 0;
        double LB = 0;

        // 遍历云任务列表
        for (Cloudlet cloudlet : cloudletList) {
            // 打印云任务ID
            Log.print(indent + cloudlet.getCloudletId() + indent + indent);

            // 检查云任务状态，只有成功完成的任务才会被处理
            if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
                // 更新总耗时
                double finishTime = cloudlet.getFinishTime();
                if (finishTime > makespan) {
                    makespan = finishTime;
                }
                // 获取虚拟机ID和实际CPU执行时间，并更新对应虚拟机的执行时间和总成本
                int vmId = cloudlet.getVmId();
                double actualCPUTime = cloudlet.getActualCPUTime();
                executeTimeOfVM[vmId] += actualCPUTime;
                cost += actualCPUTime * cloudlet.getCostPerSec();

                // 打印任务状态、资源ID、VM ID、提交时间、开始执行时间和完成时间、带宽成本和CPU成本
                Log.print("SUCCESS");
                Log.printLine(indent + indent + cloudlet.getResourceId()
                        + indent + indent + indent + cloudlet.getVmId()
                        + indent + indent
                        + dft.format(cloudlet.getSubmissionTime()) + indent
                        + indent + dft.format(cloudlet.getExecStartTime())
                        + indent + indent
                        + dft.format(finishTime)
                        + indent + indent + indent + dft.format(cloudlet.getProcessingCost())
                        + indent + indent + indent + dft.format(actualCPUTime * cloudlet.getCostPerSec()));
            }
        }
        // 计算所有虚拟机的平均执行时间
        double avgExecuteTime = Arrays.stream(executeTimeOfVM).average().getAsDouble();
        // 计算负载均衡度（LB）
        for (int i = 0; i < vmNum; i++) {
            LB += Math.pow(executeTimeOfVM[i] - avgExecuteTime, 2);
        }
        LB = Math.sqrt(LB / vmNum);

        // 输出总耗时、负载均衡度和总成本
        double finalMakespan = makespan;
        Log.printLine("makespan: " + finalMakespan);
        Log.printLine("LB: " + LB);
        Log.printLine("cost: " + cost);
    }

}
