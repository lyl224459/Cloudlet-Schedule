package CloudletScheduler.runs;

import CloudletScheduler.MOOptimizer.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;

import java.util.ArrayList;
import java.util.List;

/**
 * Pareto前沿结果类：用于存储多目标优化算法的Pareto前沿数据
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
                // 多目标优化四个目标：makespan, cost, loadBalance, resourceUtilization
                if (obj.getValues() != null && obj.getValues().length >= 4) {
                    solutions.add(new ParetoSolution(
                        obj.getValues()[0],  // makespan
                        obj.getValues()[1],  // cost
                        obj.getValues()[2],  // loadBalance
                        obj.getValues()[3]   // resourceUtilization
                    ));
                } else if (obj.getValues() != null && obj.getValues().length >= 3) {
                    // 向后兼容：如果只有3个目标，resourceUtilization设为0
                    solutions.add(new ParetoSolution(
                        obj.getValues()[0],  // makespan
                        obj.getValues()[1],  // cost
                        obj.getValues()[2],  // loadBalance
                        0.0                  // resourceUtilization (默认值)
                    ));
                }
            }
        }
        this.archiveSize = solutions.size();
    }

    /**
     * Pareto解的数据类
     */
    public static class ParetoSolution {
        public final double makespan;
        public final double cost;
        public final double loadBalance;
        public final double resourceUtilization;

        public ParetoSolution(double makespan, double cost, double loadBalance, double resourceUtilization) {
            this.makespan = makespan;
            this.cost = cost;
            this.loadBalance = loadBalance;
            this.resourceUtilization = resourceUtilization;
        }
    }
}
