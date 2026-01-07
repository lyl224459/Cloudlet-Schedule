// File: CloudletScheduler.datacenter.MultiObjectiveEvaluator.java

package CloudletScheduler.datacenter;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.List;
import java.util.Arrays;

/**
 * 多目标评估器：用于计算调度方案在多个目标维度上的性能指标。
 * 包括：Makespan（最大完成时间）、成本、负载均衡度（LB）、资源利用率等。
 */
public class MultiObjectiveEvaluator {

    private final List<Cloudlet> cloudletList;
    private final List<Vm> vmList;
    private final int cloudletNum;
    private final int vmNum;
    
    // 归一化边界值（用于归一化目标函数）
    private Double minMakespan = null;
    private Double maxMakespan = null;
    private Double minCost = null;
    private Double maxCost = null;
    private Double minLoadBalance = null;
    private Double maxLoadBalance = null;
    private Double minResourceUtilization = null;
    private Double maxResourceUtilization = null;
    
    // 是否启用归一化
    private boolean normalizeEnabled = false;

    public MultiObjectiveEvaluator(List<Cloudlet> cloudletList, List<Vm> vmList) {
        this.cloudletList = cloudletList;
        this.vmList = vmList;
        this.cloudletNum = cloudletList.size();
        this.vmNum = vmList.size();
    }
    
    /**
     * 启用归一化功能（需要在评估前调用estimateBounds来估算边界值）
     */
    public void enableNormalization() {
        this.normalizeEnabled = true;
        estimateBounds();
    }
    
    /**
     * 禁用归一化功能
     */
    public void disableNormalization() {
        this.normalizeEnabled = false;
    }

    /**
     * 计算调度方案的多个目标值。
     *
     * @param cloudletToVm 云任务到虚拟机的映射
     * @return 包含所有目标值的对象
     */
    public ObjectiveValues evaluate(int[] cloudletToVm) {
        double makespan = estimateMakespan(cloudletToVm);
        double cost = estimateCost(cloudletToVm);
        double lb = estimateLB(cloudletToVm);
        double resourceUtilization = estimateResourceUtilization(cloudletToVm);

        // 如果启用归一化，对目标值进行归一化
        if (normalizeEnabled) {
            makespan = normalizeMakespan(makespan);
            cost = normalizeCost(cost);
            lb = normalizeLoadBalance(lb);
            resourceUtilization = normalizeResourceUtilization(resourceUtilization);
        }

        return new ObjectiveValues(makespan, cost, lb, resourceUtilization);
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

    /**
     * 修复后的LoadBalance计算：使用变异系数（Coefficient of Variation）
     * 修复了原代码中avgExecuteTime计算错误的问题
     */
    private double estimateLB(int[] cloudletToVm) {
        double[] executeTimeOfVM = new double[vmNum];

        // 计算每个VM的执行时间
        for (int i = 0; i < cloudletNum; i++) {
            long length = cloudletList.get(i).getCloudletLength();
            int vmId = cloudletToVm[i];
            executeTimeOfVM[vmId] += (double) length / vmList.get(vmId).getMips();
        }

        // 计算所有VM的平均执行时间（修复：正确计算总时间）
        double totalTime = 0;
        for (int i = 0; i < vmNum; i++) {
            totalTime += executeTimeOfVM[i];
        }
        double avgExecuteTime = totalTime / vmNum;

        // 计算标准差
        double variance = 0;
        for (int i = 0; i < vmNum; i++) {
            variance += Math.pow(executeTimeOfVM[i] - avgExecuteTime, 2);
        }
        double stdDev = Math.sqrt(variance / vmNum);

        // 使用变异系数（CV = 标准差 / 平均值），无量纲，便于比较
        return avgExecuteTime > 0 ? stdDev / avgExecuteTime : 0.0;
    }
    
    /**
     * 计算资源利用率（Resource Utilization）
     * 衡量VM资源的利用效率：实际使用的MIPS / 总可用MIPS
     * 返回：1 - utilization（转换为最小化问题）
     */
    private double estimateResourceUtilization(int[] cloudletToVm) {
        // 计算总可用MIPS
        double totalMips = 0;
        for (int i = 0; i < vmNum; i++) {
            totalMips += vmList.get(i).getMips();
        }
        
        // 计算每个VM的执行时间
        double[] executeTimeOfVM = new double[vmNum];
        double totalWorkload = 0;
        
        for (int i = 0; i < cloudletNum; i++) {
            long length = cloudletList.get(i).getCloudletLength();
            int vmId = cloudletToVm[i];
            double execTime = (double) length / vmList.get(vmId).getMips();
            executeTimeOfVM[vmId] += execTime;
            totalWorkload += length;
        }
        
        // 计算makespan
        double makespan = Arrays.stream(executeTimeOfVM).max().orElse(0.0);
        
        // 理想利用率 = 总任务工作量 / (总MIPS × makespan)
        double idealUtilization = (makespan > 0 && totalMips > 0) ? 
            totalWorkload / (totalMips * makespan) : 0.0;
        
        // 转换为最小化问题：1 - utilization（利用率越高，值越小）
        return 1.0 - idealUtilization;
    }
    
    /**
     * 估算目标函数的边界值（用于归一化）
     */
    private void estimateBounds() {
        // 估算makespan边界
        double totalWorkload = 0;
        for (int i = 0; i < cloudletNum; i++) {
            totalWorkload += cloudletList.get(i).getCloudletLength();
        }
        
        // 最小makespan：所有任务分配给最快的VM
        double maxMips = vmList.stream().mapToDouble(Vm::getMips).max().orElse(1.0);
        minMakespan = totalWorkload / maxMips;
        
        // 最大makespan：所有任务分配给最慢的VM
        double minMips = vmList.stream().mapToDouble(Vm::getMips).min().orElse(1.0);
        maxMakespan = totalWorkload / minMips;
        
        // 估算cost边界
        // 最小cost：所有任务分配给最便宜的VM
        minCost = totalWorkload / maxMips * Constants.L_PRICE;
        
        // 最大cost：所有任务分配给最贵的VM
        maxCost = totalWorkload / minMips * Constants.H_PRICE;
        
        // 估算LoadBalance边界
        // 最小LB：完全均衡（理想情况）
        minLoadBalance = 0.0;
        
        // 最大LB：所有任务分配给一个VM（最不均衡）
        double totalMips = vmList.stream().mapToDouble(Vm::getMips).sum();
        double avgMips = totalMips / vmNum;
        double avgTime = totalWorkload / (vmNum * avgMips);
        maxLoadBalance = Math.sqrt(vmNum - 1) * avgTime; // 近似值
        
        // 估算ResourceUtilization边界
        minResourceUtilization = 0.0; // 完全利用
        maxResourceUtilization = 1.0; // 完全不利用
    }
    
    /**
     * 归一化函数
     */
    private double normalizeMakespan(double makespan) {
        if (minMakespan == null || maxMakespan == null || maxMakespan <= minMakespan) {
            return makespan;
        }
        return (makespan - minMakespan) / (maxMakespan - minMakespan);
    }
    
    private double normalizeCost(double cost) {
        if (minCost == null || maxCost == null || maxCost <= minCost) {
            return cost;
        }
        return (cost - minCost) / (maxCost - minCost);
    }
    
    private double normalizeLoadBalance(double lb) {
        if (minLoadBalance == null || maxLoadBalance == null || maxLoadBalance <= minLoadBalance) {
            return lb;
        }
        return (lb - minLoadBalance) / (maxLoadBalance - minLoadBalance);
    }
    
    private double normalizeResourceUtilization(double ru) {
        if (minResourceUtilization == null || maxResourceUtilization == null || 
            maxResourceUtilization <= minResourceUtilization) {
            return ru;
        }
        return (ru - minResourceUtilization) / (maxResourceUtilization - minResourceUtilization);
    }
}