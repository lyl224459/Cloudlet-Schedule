package CloudletScheduler.Optimizer.dbo;

import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.runs.MainRunner;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * DBO-based Scheduler for Cloudlet-VM allocation
 */
public class DBOScheduler extends Scheduler {

    private DungBeetleOptimizer dbo;

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_ITER = MainRunner.Config.MAX_ITER;

    public DBOScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        this.dbo = new DungBeetleOptimizer(
                this::estimateFitness,
                POPULATION,
                0,
                vmNum - 1,
                cloudletNum,
                MAX_ITER
        );
        Log.printLine("Using DBO scheduler");
    }

    @Override
    public int[] allocate() {
        return dbo.execute();
    }
}