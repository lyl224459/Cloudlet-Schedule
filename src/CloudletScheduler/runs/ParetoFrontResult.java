package CloudletScheduler.runs;

import CloudletScheduler.MOOptimizer.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;

import java.util.ArrayList;
import java.util.List;

/**
 * Pareto前沿结果类：用于存储多目标优化算法的Pareto前沿数据
 * 
 * 注意：此类的字段名使用通用名称（makespan, costEfficiency, loadBalanceIndex, resourceWaste），
 * 但实际值来自ObjectiveValues，支持新的目标函数设计。
 */
public class ParetoFrontResult {
    public final List<ParetoSolution> solutions;
    public final int archiveSize;

    public ParetoFrontResult(ParetoArchive archive) {
        this.solutions = new ArrayList<>();
        if (archive != null && !archive.isEmpty()) {
            List<double[]> sols = archive.getSolutions();
            List<ObjectiveValues> objs = archive.getObjectives();
            for (int i = 0; i < sols.size(); i++) {
                ObjectiveValues obj = objs.get(i);
                // 多目标优化四个目标：makespan, costEfficiency, loadBalanceIndex, resourceWaste
                if (obj.getValues() != null && obj.getValues().length >= 4) {
                    solutions.add(new ParetoSolution(
                        obj.getValues()[0],  // makespan
                        obj.getValues()[1],  // costEfficiency
                        obj.getValues()[2],  // loadBalanceIndex
                        obj.getValues()[3]   // resourceWaste
                    ));
                } else if (obj.getValues() != null && obj.getValues().length >= 3) {
                    // 向后兼容：如果只有3个目标，resourceWaste设为0
                    solutions.add(new ParetoSolution(
                        obj.getValues()[0],  // makespan
                        obj.getValues()[1],  // costEfficiency (或旧格式的cost)
                        obj.getValues()[2],  // loadBalanceIndex (或旧格式的loadBalance)
                        0.0                  // resourceWaste (默认值)
                    ));
                }
            }
        }
        this.archiveSize = solutions.size();
    }

    /**
     * Pareto解的数据类
     * 
     * 注意：字段名使用通用名称，实际值来自ObjectiveValues的values数组：
     * - makespan: 最大完成时间
     * - costEfficiency: 成本效率比（新格式）或总成本（旧格式）
     * - loadBalanceIndex: 负载均衡指数（新格式）或负载均衡度（旧格式）
     * - resourceWaste: 资源浪费率（新格式）或资源利用率（旧格式）
     */
    public static class ParetoSolution {
        public final double makespan;
        public final double costEfficiency;      // 新格式：成本效率比；旧格式：总成本
        public final double loadBalanceIndex;    // 新格式：负载均衡指数；旧格式：负载均衡度
        public final double resourceWaste;       // 新格式：资源浪费率；旧格式：资源利用率

        public ParetoSolution(double makespan, double costEfficiency, double loadBalanceIndex, double resourceWaste) {
            this.makespan = makespan;
            this.costEfficiency = costEfficiency;
            this.loadBalanceIndex = loadBalanceIndex;
            this.resourceWaste = resourceWaste;
        }
    }
}
