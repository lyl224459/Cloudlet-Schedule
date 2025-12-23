package CloudletScheduler.Optimizer.hwga;

import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.runs.MainRunner;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.util.List;

/**
 * @author : LA4AM12
 * @create : 2023-03-16 10:49:41
 * @description :
 */
public class HWGAScheduler extends Scheduler {
	private final HWGA HWGA;
	private static final int POPULATION = MainRunner.Config.POPULATION;
	private static final int MAX_ITER = MainRunner.Config.MAX_ITER;
	private static final double CROSSOVER_RATE = 0.6;
	private static final double MUTATION_RATE = 0.005;
	private static final double TOURNAMENT_RATIO = 0.15;
	private static final double PHASE_OUT_RATIO = 0.15;


	public HWGAScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
		super(cloudletList, vmList);
		this.HWGA = new HWGA(
				this::estimateMakespan,
				POPULATION,
				vmNum-1,
				cloudletNum,
				MAX_ITER,
				TOURNAMENT_RATIO,
				CROSSOVER_RATE,
				MUTATION_RATE,
				PHASE_OUT_RATIO);
		Log.printLine("Using HWGA scheduler");
	}

	@Override
	public int[] allocate() {
		return HWGA.run();
	}
}
