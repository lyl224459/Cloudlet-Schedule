package CloudletScheduler.Optimizer.gwo;

import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.runs.MainRunner;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * GWO-based Scheduler for Cloudlet-VM allocation
 */
public class GWOScheduler extends Scheduler {

    private GreyWolfOptimizer gwo;

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_ITER = MainRunner.Config.MAX_ITER;

    public GWOScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        this.gwo = new GreyWolfOptimizer(
                this::estimateFitness,
                POPULATION,
                0,
                vmNum - 1,
                cloudletNum,
                MAX_ITER
        );
        Log.printLine("Using GWO scheduler");
    }

    @Override
    public int[] allocate() {
        return gwo.execute();
    }
}