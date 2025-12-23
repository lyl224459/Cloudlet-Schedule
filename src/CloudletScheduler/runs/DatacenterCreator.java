package CloudletScheduler.runs;


import CloudletScheduler.datacenter.Constants;
import CloudletScheduler.datacenter.Type;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class DatacenterCreator {

    public static Datacenter createDatacenter(String name, Type type, Random rand) throws Exception {
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

    public static List<Vm> createVms(int userId, Random rand) {
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

    public static List<Cloudlet> createCloudlets(int userId, Random rand) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        int id = 0;
        int pesNumber = 1;
        UtilizationModel utilizationModel = new UtilizationModelFull();

        double mean = 30000.0;
        double variance = 1.5;
        for (int i = 0; i < MainRunner.Config.CLOUDLET_N; i++) {
            long length = (long) (Math.exp(rand.nextGaussian() * variance + Math.log(mean)));
            long fileSize = Math.max(10, (long) (rand.nextGaussian() * 100 + 100));
            long outputSize = Math.max(10, (long) (rand.nextGaussian() * 100 + 100));

            Cloudlet cloudlet = new Cloudlet(id++, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
            cloudlet.setUserId(userId);
            cloudletList.add(cloudlet);
        }
        return cloudletList;
    }

    public static List<Cloudlet> createCloudlets1(int userId, Random rand) {
        // 初始化云任务列表
        List<Cloudlet> cloudletList = new ArrayList<>();
        // 初始化云任务ID为0
        int id = 0;
        // 设置每个云任务使用的PE（处理元素）数量为1
        int pesNumber = 1;
        // 创建一个完全利用率模型实例，表示云任务将一直占用全部资源
        UtilizationModel utilizationModel = new UtilizationModelFull();

        // 循环创建CLOUDLET_N个云任务
        for (int i = 0; i < MainRunner.Config.CLOUDLET_N; i++) {
            // 随机生成云任务的长度（执行时间），范围在10000到50000之间
            long length = rand.nextInt(40000) + 10000;
            // 随机生成云任务的输入文件大小，范围在10到200之间
            long fileSize = rand.nextInt(190) + 10;
            // 随机生成云任务的输出文件大小，范围在10到200之间
            long outputSize = rand.nextInt(190) + 10;
            // 创建一个云任务实例
            Cloudlet cloudlet = new Cloudlet(id, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
            // 设置云任务的用户ID
            cloudlet.setUserId(userId);
            // 将云任务添加到列表中
            cloudletList.add(cloudlet);
            // 增加云任务ID，确保每个云任务有一个唯一的ID
            id++;
        }
        // 返回云任务列表
        return cloudletList;
    }

    // 设置任务执行时间的均值与方差
    private static final double MEAN_EXEC_TIME = 30000.0;  // 执行时间的均值（单位：毫秒）
    private static final double VARIANCE_EXEC_TIME = 1.5;   // 执行时间的方差

    // 文件大小的均值与方差
    private static final double MEAN_FILE_SIZE = 100.0;     // 文件大小的均值（单位：KB）
    private static final double VARIANCE_FILE_SIZE = 20.0;   // 文件大小的方差

    // 输出文件大小的均值与方差
    private static final double MEAN_OUTPUT_SIZE = 100.0;   // 输出文件大小的均值（单位：KB）
    private static final double VARIANCE_OUTPUT_SIZE = 20.0; // 输出文件大小的方差

    // 资源利用模型
    private static final UtilizationModel UTILIZATION_MODEL = new UtilizationModelFull();

    /**
     * 创建云任务列表
     *
     * @param userId 用户ID
     * @param rand 随机数生成器
     * @return 生成的任务列表
     */
    public static List<Cloudlet> createCloudletsSCI(int userId, Random rand) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        int id = 0;
        int pesNumber = 1; // 每个任务分配一个虚拟处理单元（PE）

        // 生成任务
        for (int i = 0; i < MainRunner.Config.CLOUDLET_N; i++) {
            // 生成任务的执行时间（对数正态分布）
            long length = (long) (Math.exp(rand.nextGaussian() * VARIANCE_EXEC_TIME + Math.log(MEAN_EXEC_TIME)));

            // 生成文件大小（正态分布）
            long fileSize = Math.max(10, (long) (rand.nextGaussian() * VARIANCE_FILE_SIZE + MEAN_FILE_SIZE));

            // 生成输出大小（正态分布）
            long outputSize = Math.max(10, (long) (rand.nextGaussian() * VARIANCE_OUTPUT_SIZE + MEAN_OUTPUT_SIZE));

            // 创建云任务对象
            Cloudlet cloudlet = new Cloudlet(id++, length, pesNumber, fileSize, outputSize,
                    UTILIZATION_MODEL, UTILIZATION_MODEL, UTILIZATION_MODEL);
            cloudlet.setUserId(userId);  // 设置用户ID
            cloudletList.add(cloudlet);   // 将任务添加到列表
        }

        return cloudletList;  // 返回生成的任务列表
    }
}
