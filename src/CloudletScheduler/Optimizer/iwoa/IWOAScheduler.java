package CloudletScheduler.Optimizer.iwoa;


import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.runs.MainRunner;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * @author : LA4AM12
 * @create : 2023-02-23 14:39:48
 * @description : WOA schedule Algorithm
 */
public class IWOAScheduler extends Scheduler {
	private IWhaleOptimizationAlgorithm iwoa;

	private static final int POPULATION = MainRunner.Config.POPULATION;

	private static final int MAX_ITER = MainRunner.Config.MAX_ITER;

	public IWOAScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
		super(cloudletList, vmList);
		this.iwoa = new IWhaleOptimizationAlgorithm(this::estimateFitness, POPULATION, 0, vmNum-1, cloudletNum, MAX_ITER, true);
		Log.printLine("Using IWOA scheduler");
	}

	@Override
	public int[] allocate() {
		return iwoa.execute();
	}
}
