// File: CloudletScheduler.datacenter.ObjectiveValues.java

package CloudletScheduler.datacenter;

/**
 * 封装多目标优化中的各个目标值。
 * 所有目标均为"越小越好"（minimization）。
 * 
 * 重新设计的4个目标函数：
 * 1. makespan: 最大完成时间
 * 2. costEfficiency: 成本效率比（总成本/总工作量）
 * 3. loadBalanceIndex: 负载均衡指数（归一化的负载不均衡度）
 * 4. resourceWaste: 资源浪费率（1 - 加权平均利用率）
 */
public class ObjectiveValues implements Cloneable {
    public final double makespan;           // 最大完成时间（关键路径）
    public final double costEfficiency;     // 成本效率比（单位工作量的成本）
    public final double loadBalanceIndex;   // 负载均衡指数（归一化的负载不均衡度）
    public final double resourceWaste;      // 资源浪费率（1 - 利用率）

    // 用于 Pareto 比较的统一数组（顺序必须固定！）
    public final double[] values;

    public ObjectiveValues(double makespan,
                           double costEfficiency,
                           double loadBalanceIndex,
                           double resourceWaste) {
        this.makespan = makespan;
        this.costEfficiency = costEfficiency;
        this.loadBalanceIndex = loadBalanceIndex;
        this.resourceWaste = resourceWaste;
        // 顺序必须与支配逻辑一致（全部 minimization）
        this.values = new double[]{
                makespan,
                costEfficiency,
                loadBalanceIndex,
                resourceWaste
        };
    }

    @Override
    public ObjectiveValues clone() {
        return new ObjectiveValues(makespan,
                costEfficiency,
                loadBalanceIndex,
                resourceWaste);
    }

    @Override
    public String toString() {
        return String.format(
                "Objectives{makespan=%.2f, costEfficiency=%.6f, loadBalanceIndex=%.4f, resourceWaste=%.4f}",
                makespan,
                costEfficiency,
                loadBalanceIndex,
                resourceWaste
        );
    }

    public boolean dominates(ObjectiveValues other) {
        boolean atLeastOneBetter = false;
        for (int i = 0; i < values.length; i++) {
            if (this.values[i] > other.values[i]) return false; // 假设最小化
            if (this.values[i] < other.values[i]) atLeastOneBetter = true;
        }
        return atLeastOneBetter;
    }
    
    public double[] getValues() { 
        return values; 
    }
}
