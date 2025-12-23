package CloudletScheduler.Optimizer.pso;

import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.runs.MainRunner;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * PSO-based Scheduler for Cloudlet-VM allocation
 */
public class PSOScheduler extends Scheduler {

    private ParticleSwarmOptimization pso;

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_ITER = MainRunner.Config.MAX_ITER;

    public PSOScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        this.pso = new ParticleSwarmOptimization(
                this::estimateFitness,
                POPULATION,
                0,
                vmNum - 1,
                cloudletNum,
                MAX_ITER
        );
        Log.printLine("Using PSO scheduler");
    }

    @Override
    public int[] allocate() {
        return pso.execute();
    }
}
