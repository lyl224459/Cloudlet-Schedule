// File: CloudletScheduler.datacenter.MultiObjectiveEvaluator.java

package CloudletScheduler.datacenter;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * 多目标评估器：用于计算调度方案在多个目标维度上的性能指标。
 * 包括：Makespan（最大完成时间）、总执行时间、成本、负载均衡度（LB）等。
 */
public class MultiObjectiveEvaluator {

    private final List<Cloudlet> cloudletList;
    private final List<Vm> vmList;
    private final int cloudletNum;
    private final int vmNum;

    public MultiObjectiveEvaluator(List<Cloudlet> cloudletList, List<Vm> vmList) {
        this.cloudletList = cloudletList;
        this.vmList = vmList;
        this.cloudletNum = cloudletList.size();
        this.vmNum = vmList.size();
    }

    /**
     * 计算调度方案的多个目标值。
     *
     * @param cloudletToVm 云任务到虚拟机的映射
     * @return 包含所有目标值的对象
     */
    public ObjectiveValues evaluate(int[] cloudletToVm) {
        double makespan = estimateMakespan(cloudletToVm);
//        double totalTime = estimateTotalTime(cloudletToVm);
        double cost = estimateCost(cloudletToVm);
        double lb = estimateLB(cloudletToVm);

        return new ObjectiveValues(makespan,
//                totalTime,
                cost,
                lb);
    }

    // ========== 以下方法与原 Scheduler 类中的相同，可复用 ==========

    private double estimateMakespan(int[] cloudletToVm) {
        double[] executeTimeOfVM = new double[vmNum];
        for (int i = 0; i < cloudletNum; i++) {
            long length = cloudletList.get(i).getCloudletLength();
            int vmId = cloudletToVm[i];
            executeTimeOfVM[vmId] += (double) length / vmList.get(vmId).getMips();
        }
        return java.util.Arrays.stream(executeTimeOfVM).max().orElse(0.0);
    }

    private double estimateTotalTime(int[] cloudletToVm) {
        double totalTime = 0;
        for (int i = 0; i < cloudletNum; i++) {
            long length = cloudletList.get(i).getCloudletLength();
            int vmId = cloudletToVm[i];
            totalTime += (double) length / vmList.get(vmId).getMips();
        }
        return totalTime;
    }

    private double estimateCost(int[] cloudletToVm) {
        double cost = 0;
        for (int i = 0; i < cloudletNum; i++) {
            long length = cloudletList.get(i).getCloudletLength();
            int vmId = cloudletToVm[i];
            double mips = vmList.get(vmId).getMips();
            double costPerSec = 0;

            if (mips == Constants.L_MIPS) {
                costPerSec = Constants.L_PRICE;
            } else if (mips == Constants.M_MIPS) {
                costPerSec = Constants.M_PRICE;
            } else if (mips == Constants.H_MIPS) {
                costPerSec = Constants.H_PRICE;
            }

            cost += (double) length / mips * costPerSec;
        }
        return cost;
    }

    private double estimateLB(int[] cloudletToVm) {
        double[] executeTimeOfVM = new double[vmNum];
        double avgExecuteTime = 0;

        for (int i = 0; i < cloudletNum; i++) {
            long length = cloudletList.get(i).getCloudletLength();
            int vmId = cloudletToVm[i];
            double execTime = (double) length / vmList.get(vmId).getMips();
            executeTimeOfVM[vmId] += execTime;
            avgExecuteTime += execTime;
        }
        avgExecuteTime /= vmNum;

        double LB = 0;
        for (int i = 0; i < vmNum; i++) {
            LB += Math.pow(executeTimeOfVM[i] - avgExecuteTime, 2);
        }
        return Math.sqrt(LB / vmNum);
    }
}