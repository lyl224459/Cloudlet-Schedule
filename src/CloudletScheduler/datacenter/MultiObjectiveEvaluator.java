// File: CloudletScheduler.datacenter.MultiObjectiveEvaluator.java

package CloudletScheduler.datacenter;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.List;
import java.util.Arrays;

/**
 * 多目标评估器：用于计算调度方案在多个目标维度上的性能指标。
 * 
 * 重新设计的4个目标函数（确保独立性和权衡关系）：
 * 1. Makespan（最大完成时间）：最小化所有VM中的最大执行时间
 * 2. Cost Efficiency（成本效率比）：最小化总成本/总工作量，反映单位工作量的成本
 * 3. Load Balance Index（负载均衡指数）：最小化归一化的负载不均衡度，考虑VM性能差异
 * 4. Resource Waste（资源浪费率）：最小化空闲资源比例，反映资源利用效率
 * 
 * 设计原则：
 * - 所有目标均为最小化（越小越好）
 * - 目标之间应存在权衡关系，而非强相关性
 * - 与单目标函数完全独立，不影响单目标优化
 */
public class MultiObjectiveEvaluator {

    private final List<Cloudlet> cloudletList;
    private final List<Vm> vmList;
    private final int cloudletNum;
    private final int vmNum;
    
    // 归一化边界值（用于归一化目标函数）
    private Double minMakespan = null;
    private Double maxMakespan = null;
    private Double minCostEfficiency = null;
    private Double maxCostEfficiency = null;
    private Double minLoadBalanceIndex = null;
    private Double maxLoadBalanceIndex = null;
    private Double minResourceWaste = null;
    private Double maxResourceWaste = null;
    
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
        double costEfficiency = estimateCostEfficiency(cloudletToVm);
        double loadBalanceIndex = estimateLoadBalanceIndex(cloudletToVm);
        double resourceWaste = estimateResourceWaste(cloudletToVm);

        // 如果启用归一化，对目标值进行归一化
        if (normalizeEnabled) {
            makespan = normalizeMakespan(makespan);
            costEfficiency = normalizeCostEfficiency(costEfficiency);
            loadBalanceIndex = normalizeLoadBalanceIndex(loadBalanceIndex);
            resourceWaste = normalizeResourceWaste(resourceWaste);
        }

        return new ObjectiveValues(makespan, costEfficiency, loadBalanceIndex, resourceWaste);
    }

    // ========== 目标函数1：Makespan（最大完成时间）==========
    
    /**
     * 计算Makespan（最大完成时间）
     * 目标：最小化所有VM中的最大执行时间
     * 与其他目标的关系：与Cost Efficiency可能存在权衡（高性能VM快但贵）
     */
    private double estimateMakespan(int[] cloudletToVm) {
        double[] executeTimeOfVM = new double[vmNum];
        for (int i = 0; i < cloudletNum; i++) {
            long length = cloudletList.get(i).getCloudletLength();
            int vmId = cloudletToVm[i];
            executeTimeOfVM[vmId] += (double) length / vmList.get(vmId).getMips();
        }
        return Arrays.stream(executeTimeOfVM).max().orElse(0.0);
    }

    // ========== 目标函数2：Cost Efficiency（成本效率比）==========
    
    /**
     * 计算Cost Efficiency（成本效率比）
     * 定义：总成本 / 总工作量（MI，Million Instructions）
     * 目标：最小化单位工作量的成本
     * 
     * 设计理由：
     * - 与Makespan形成权衡：使用高性能VM可以降低Makespan，但可能增加成本效率比
     * - 与单目标的Cost不同：单目标是总成本，这里是单位成本，更独立
     * - 反映成本效益：帮助选择性价比高的VM组合
     */
    private double estimateCostEfficiency(int[] cloudletToVm) {
        double totalCost = 0;
        long totalWorkload = 0;
        
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

            totalCost += (double) length / mips * costPerSec;
            totalWorkload += length;
        }
        
        // 成本效率比 = 总成本 / 总工作量（单位：$/MI）
        // 如果总工作量为0，返回一个很大的值
        return totalWorkload > 0 ? totalCost / totalWorkload : Double.MAX_VALUE;
    }

    // ========== 目标函数3：Load Balance Index（负载均衡指数）==========
    
    /**
     * 计算Load Balance Index（负载均衡指数）
     * 定义：归一化的负载不均衡度，考虑VM性能差异
     * 目标：最小化负载不均衡度
     * 
     * 设计理由：
     * - 考虑VM性能差异：使用归一化的负载分布，而非简单的执行时间差异
     * - 与Makespan形成权衡：完全均衡可能导致Makespan增加
     * - 与单目标的LB不同：单目标使用标准差，这里使用归一化的变异系数，更独立
     * 
     * 计算方法：
     * 1. 计算每个VM的归一化负载（考虑VM性能）
     * 2. 计算归一化负载的变异系数
     */
    private double estimateLoadBalanceIndex(int[] cloudletToVm) {
        // 计算每个VM的执行时间和MIPS
        double[] executeTimeOfVM = new double[vmNum];
        double[] mipsOfVM = new double[vmNum];
        
        for (int i = 0; i < cloudletNum; i++) {
            long length = cloudletList.get(i).getCloudletLength();
            int vmId = cloudletToVm[i];
            executeTimeOfVM[vmId] += (double) length / vmList.get(vmId).getMips();
            mipsOfVM[vmId] = vmList.get(vmId).getMips();
        }
        
        // 计算归一化负载（考虑VM性能差异）
        // 归一化负载 = 执行时间 / VM的MIPS（反映VM的利用率）
        double[] normalizedLoad = new double[vmNum];
        for (int i = 0; i < vmNum; i++) {
            normalizedLoad[i] = mipsOfVM[i] > 0 ? executeTimeOfVM[i] / mipsOfVM[i] : 0.0;
        }
        
        // 计算归一化负载的平均值
        double avgNormalizedLoad = Arrays.stream(normalizedLoad).average().orElse(0.0);
        
        if (avgNormalizedLoad <= 0) {
            return 0.0; // 完全均衡
        }
        
        // 计算标准差
        double variance = 0;
        for (int i = 0; i < vmNum; i++) {
            variance += Math.pow(normalizedLoad[i] - avgNormalizedLoad, 2);
        }
        double stdDev = Math.sqrt(variance / vmNum);
        
        // 使用变异系数（CV = 标准差 / 平均值）
        return stdDev / avgNormalizedLoad;
    }

    // ========== 目标函数4：Resource Waste（资源浪费率）==========
    
    /**
     * 计算Resource Waste（资源浪费率）
     * 定义：空闲资源比例 = 1 - 实际利用率
     * 目标：最小化资源浪费率
     * 
     * 设计理由：
     * - 与Makespan独立：即使Makespan相同，资源浪费率可能不同
     * - 反映资源利用效率：帮助识别资源浪费的分配方案
     * - 与单目标的ResourceUtilization不同：单目标是利用率，这里是浪费率，更独立
     * 
     * 计算方法：
     * 1. 计算每个VM的实际利用率（执行时间 / makespan）
     * 2. 计算加权平均利用率（按MIPS加权）
     * 3. 资源浪费率 = 1 - 加权平均利用率
     */
    private double estimateResourceWaste(int[] cloudletToVm) {
        // 计算每个VM的执行时间和MIPS
        double[] executeTimeOfVM = new double[vmNum];
        double[] mipsOfVM = new double[vmNum];
        double totalMips = 0;
        
        for (int i = 0; i < vmNum; i++) {
            mipsOfVM[i] = vmList.get(i).getMips();
            totalMips += mipsOfVM[i];
        }
        
        for (int i = 0; i < cloudletNum; i++) {
            long length = cloudletList.get(i).getCloudletLength();
            int vmId = cloudletToVm[i];
            executeTimeOfVM[vmId] += (double) length / vmList.get(vmId).getMips();
        }
        
        // 计算makespan
        double makespan = Arrays.stream(executeTimeOfVM).max().orElse(0.0);
        
        if (makespan <= 0 || totalMips <= 0) {
            return 1.0; // 完全浪费
        }
        
        // 计算每个VM的利用率（执行时间 / makespan）
        double[] utilizationOfVM = new double[vmNum];
        for (int i = 0; i < vmNum; i++) {
            utilizationOfVM[i] = executeTimeOfVM[i] / makespan;
        }
        
        // 计算加权平均利用率（按MIPS加权）
        double weightedAvgUtilization = 0;
        for (int i = 0; i < vmNum; i++) {
            weightedAvgUtilization += utilizationOfVM[i] * (mipsOfVM[i] / totalMips);
        }
        
        // 资源浪费率 = 1 - 加权平均利用率
        return 1.0 - weightedAvgUtilization;
    }
    
    // ========== 归一化边界估算 ==========
    
    /**
     * 估算目标函数的边界值（用于归一化）
     */
    private void estimateBounds() {
        // 计算总工作量
        long totalWorkload = 0;
        for (int i = 0; i < cloudletNum; i++) {
            totalWorkload += cloudletList.get(i).getCloudletLength();
        }
        
        // 获取VM性能范围
        double maxMips = vmList.stream().mapToDouble(Vm::getMips).max().orElse(1.0);
        double minMips = vmList.stream().mapToDouble(Vm::getMips).min().orElse(1.0);
        double totalMips = vmList.stream().mapToDouble(Vm::getMips).sum();
        
        // 1. Makespan边界
        minMakespan = totalWorkload / maxMips; // 所有任务分配给最快的VM
        maxMakespan = totalWorkload / minMips; // 所有任务分配给最慢的VM
        
        // 2. Cost Efficiency边界
        // 最小：所有任务分配给最便宜的VM（假设L_PRICE/MIPS最小）
        double minCostPerMips = Math.min(
            Constants.L_PRICE / Constants.L_MIPS,
            Math.min(Constants.M_PRICE / Constants.M_MIPS, Constants.H_PRICE / Constants.H_MIPS)
        );
        minCostEfficiency = minCostPerMips;
        
        // 最大：所有任务分配给最贵的VM（假设H_PRICE/MIPS最大）
        double maxCostPerMips = Math.max(
            Constants.L_PRICE / Constants.L_MIPS,
            Math.max(Constants.M_PRICE / Constants.M_MIPS, Constants.H_PRICE / Constants.H_MIPS)
        );
        maxCostEfficiency = maxCostPerMips;
        
        // 3. Load Balance Index边界
        minLoadBalanceIndex = 0.0; // 完全均衡
        // 最大：所有任务分配给一个VM（最不均衡）
        // 近似值：使用变异系数的理论最大值
        maxLoadBalanceIndex = Math.sqrt(vmNum - 1); // 近似值
        
        // 4. Resource Waste边界
        minResourceWaste = 0.0; // 完全利用
        maxResourceWaste = 1.0; // 完全不利用
    }
    
    // ========== 归一化函数 ==========
    
    private double normalizeMakespan(double makespan) {
        if (minMakespan == null || maxMakespan == null || maxMakespan <= minMakespan) {
            return makespan;
        }
        return (makespan - minMakespan) / (maxMakespan - minMakespan);
    }
    
    private double normalizeCostEfficiency(double costEfficiency) {
        if (minCostEfficiency == null || maxCostEfficiency == null || maxCostEfficiency <= minCostEfficiency) {
            return costEfficiency;
        }
        return (costEfficiency - minCostEfficiency) / (maxCostEfficiency - minCostEfficiency);
    }
    
    private double normalizeLoadBalanceIndex(double loadBalanceIndex) {
        if (minLoadBalanceIndex == null || maxLoadBalanceIndex == null || maxLoadBalanceIndex <= minLoadBalanceIndex) {
            return loadBalanceIndex;
        }
        return (loadBalanceIndex - minLoadBalanceIndex) / (maxLoadBalanceIndex - minLoadBalanceIndex);
    }
    
    private double normalizeResourceWaste(double resourceWaste) {
        if (minResourceWaste == null || maxResourceWaste == null || maxResourceWaste <= minResourceWaste) {
            return resourceWaste;
        }
        return (resourceWaste - minResourceWaste) / (maxResourceWaste - minResourceWaste);
    }
}
