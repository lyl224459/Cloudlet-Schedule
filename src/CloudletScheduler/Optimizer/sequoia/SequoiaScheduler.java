package CloudletScheduler.Optimizer.sequoia;

import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.runs.MainRunner;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * 使用 Sequoia 优化算法进行云任务调度
 */
public class SequoiaScheduler extends Scheduler {
    private final CloudletScheduler.Optimizer.sequoia.SequoiaOptimizationAlgorithm sequoia;

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_ITER = MainRunner.Config.MAX_ITER;

    public SequoiaScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        this.sequoia = new CloudletScheduler.Optimizer.sequoia.SequoiaOptimizationAlgorithm(
                this::estimateFitness,
                POPULATION,
                MAX_ITER,
                0,
                vmNum - 1,
                cloudletNum
        );
        Log.printLine("Using SequoiaOA scheduler");
    }

    @Override
    public int[] allocate() {
        return sequoia.execute();
    }
}