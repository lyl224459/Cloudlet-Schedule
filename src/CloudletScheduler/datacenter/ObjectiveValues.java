// File: CloudletScheduler.datacenter.ObjectiveValues.java

package CloudletScheduler.datacenter;

/**
 * 封装多目标优化中的各个目标值。
 * 所有目标均为"越小越好"（minimization）。
 */
public class ObjectiveValues implements Cloneable {
    public final double makespan;           // 最大完成时间（关键路径）
//    public final double totalTime;        // 所有任务总执行时间
    public final double cost;               // 总成本
    public final double loadBalance;        // 负载均衡度（变异系数）
    public final double resourceUtilization; // 资源利用率（转换为最小化：1 - utilization）

    // 新增：用于 Pareto 比较的统一数组（顺序必须固定！）
    public final double[] values;

    public ObjectiveValues(double makespan,
//                           double totalTime,
                           double cost,
                           double loadBalance,
                           double resourceUtilization) {
        this.makespan = makespan;
//        this.totalTime = totalTime;
        this.cost = cost;
        this.loadBalance = loadBalance;
        this.resourceUtilization = resourceUtilization;
        // 顺序必须与支配逻辑一致（全部 minimization）
        this.values = new double[]{
                makespan,
//                totalTime,
                cost,
                loadBalance,
                resourceUtilization
        };
    }

    @Override
    public ObjectiveValues clone() {
        return new ObjectiveValues(makespan,
//                totalTime,
                cost,
                loadBalance,
                resourceUtilization);
    }

    @Override
    public String toString() {
        return String.format(
                "Objectives{makespan=%.2f, cost=%.2f, LB=%.2f, RU=%.4f}",
                makespan,
                cost,
                loadBalance,
                resourceUtilization
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
    public double[] getValues() { return values; }
}