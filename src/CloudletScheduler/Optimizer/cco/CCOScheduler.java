package CloudletScheduler.Optimizer.cco;

import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.runs.MainRunner;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

public class CCOScheduler extends Scheduler {

    private CoralReefOptimizer cco;

    private static final int POPULATION = MainRunner.Config.POPULATION;
    private static final int MAX_ITER = MainRunner.Config.MAX_ITER;

    public CCOScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        super(cloudletList, vmList);
        this.cco = new CoralReefOptimizer(
                this::estimateFitness,
                POPULATION,
                0,
                vmNum - 1,
                cloudletNum,
                MAX_ITER
        );
        Log.printLine("Using CCO scheduler");
    }

    @Override
    public int[] allocate() {
        return cco.execute();
    }
}