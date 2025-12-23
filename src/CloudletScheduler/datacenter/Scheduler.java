package CloudletScheduler.datacenter;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * @author : LA4AM12
 * @create : 2023-02-23 09:26:19
 * @description : Mapping cloudlets to Vms using fitness function
 */
public abstract class Scheduler {
	// cost
	private static final double ALPHA = 1.0/3;
	// total time
	private static final double BETA = 1.0/3;
	// LB
	private static final double GAMMA = 1.0/3;
	protected List<Cloudlet> cloudletList;
	protected List<Vm> vmList;
	protected int cloudletNum;
	protected int vmNum;
	private int[] randomCloudletToVm;


	public Scheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
		this.cloudletList = cloudletList;
		this.vmList = vmList;
		cloudletNum = cloudletList.size();
		vmNum = vmList.size();
		randomCloudletToVm = new int[cloudletNum];
		Random random = new Random();
		for (int i = 0; i < cloudletNum; i++) {
			randomCloudletToVm[i] = random.nextInt(vmNum);
		}
	}

	public abstract int[] allocate();

	/**
	 * 调度云任务到虚拟机
	 * 此方法首先分配云任务到合适的虚拟机，然后更新每个云任务的虚拟机ID，
	 * 并打印出调度方案的估计值，包括最大完成时间、负载均衡度、成本、总时间和适应度
	 */
	public void schedule() {
	    // 分配云任务到虚拟机
	    int[] cloudletToVm = allocate();

	    // 更新每个云任务的虚拟机ID
	    for (int i = 0; i < cloudletNum; i++) {
	        cloudletList.get(i).setVmId(cloudletToVm[i]);
	    }

	    // 打印估计的最大完成时间
	    Log.printLine("estimate time span: " + estimateMakespan(cloudletToVm));

	    // 打印估计的负载均衡度
	    Log.printLine("estimate LB: " + estimateLB(cloudletToVm));

	    // 打印估计的成本
	    Log.printLine("estimate cost: " + estimateCost(cloudletToVm));

	    // 打印估计的总时间
	    Log.printLine("estimate totalTime: " + estimateTotalTime(cloudletToVm));

	    // 打印估计的适应度
	    Log.printLine("estimate fitness: " + estimateFitness(cloudletToVm));
	}

	/**
	 * 根据云任务到虚拟机的分配情况，估算Load Balancing（LB）值
	 * LB值用于衡量虚拟机之间的负载均衡程度，LB值越小表示负载分配越均衡
	 *
	 * @param cloudletToVm 一个数组，表示每个云任务分配给的虚拟机ID
	 * @return 返回计算得到的LB值
	 */
	public double estimateLB(int[] cloudletToVm) {
	    // 初始化一个数组，用于存储每台虚拟机的执行时间
	    double[] executeTimeOfVM = new double[vmNum];
	    // 初始化平均执行时间
	    double avgExecuteTime = 0;

	    // 遍历所有云任务，计算每个任务的执行时间，并累加到相应虚拟机的执行时间中
	    for (int i = 0; i < cloudletNum; i++) {
	        // 获取当前云任务的长度
	        long length = cloudletList.get(i).getCloudletLength();
	        // 获取当前云任务分配的虚拟机ID
	        int vmId = cloudletToVm[i];
	        // 计算当前云任务在分配的虚拟机上的执行时间
	        double execTime = length / vmList.get(vmId).getMips();
	        // 累加执行时间到对应虚拟机
	        executeTimeOfVM[vmId] += execTime;
	        // 累加执行时间到总执行时间，用于后续计算平均执行时间
	        avgExecuteTime += execTime;
	    }
	    // 计算虚拟机的平均执行时间
	    avgExecuteTime /= vmNum;

	    // 初始化LB值累加器
	    double LB = 0;
	    // 遍历所有虚拟机，计算每台虚拟机执行时间与平均执行时间的平方差，并累加到LB值累加器
	    for (int i = 0; i < vmNum; i++) {
	        LB += Math.pow(executeTimeOfVM[i] - avgExecuteTime, 2);
	    }
	    // 计算LB值，即每台虚拟机执行时间与平均执行时间的平方差的平均值的平方根
	    LB = Math.sqrt(LB / vmNum);

	    // 返回计算得到的LB值
	    return LB;
	}

	/**
	 * 根据云任务到虚拟机的分配情况估计最大完成时间（Makespan）
	 * 最大完成时间是指所有任务完成所需的最长时间，用于评估任务调度的效率
	 *
	 * @param cloudletToVm 分配云任务到虚拟机的映射数组，每个元素表示对应云任务分配的虚拟机ID
	 * @return 返回估计的最大完成时间
	 */
	public double estimateMakespan(int[] cloudletToVm) {
	    // 初始化一个数组，用于记录每台虚拟机的执行时间
	    double[] executeTimeOfVM = new double[vmNum];

	    // 遍历所有云任务，计算每个虚拟机的总执行时间
	    for (int i = 0; i < cloudletNum; i++) {
	        // 获取当前云任务的执行长度
	        long length = cloudletList.get(i).getCloudletLength();
	        // 获取当前云任务分配的虚拟机ID
	        int vmId = cloudletToVm[i];
	        // 累加该虚拟机的执行时间，执行时间 = 任务长度 / 虚拟机的MIPS
	        executeTimeOfVM[vmId] += length / vmList.get(vmId).getMips();
	    }

	    // 返回所有虚拟机中执行时间最长的时间，即为最大完成时间
	    return Arrays.stream(executeTimeOfVM).max().getAsDouble();
	}

	/**
	 * 估算运行所有云任务的总成本
	 * 此方法根据云任务分配到的虚拟机的性能来计算成本每个云任务在特定虚拟机上的执行时间，
	 * 再乘以该虚拟机每秒的费用，得到总成本
	 *
	 * @param cloudletToVm 一个数组，指示每个云任务分配给哪个虚拟机
	 * @return 返回运行所有云任务的总成本
	 */
	public double estimateCost(int[] cloudletToVm) {
	    // 初始化总成本为0
	    double cost = 0;
	    // 初始化每秒成本，根据虚拟机性能不同而变化
	    double costPerSec = 0;

	    // 遍历所有云任务
	    for (int i = 0; i < cloudletNum; i++) {
	        // 获取当前云任务的长度（执行时间）
	        long length = cloudletList.get(i).getCloudletLength();
	        // 获取分配给当前云任务的虚拟机的MIPS性能
	        double mips = vmList.get(cloudletToVm[i]).getMips();

	        // 根据虚拟机的MIPS性能确定每秒的费用
	        if (mips == Constants.L_MIPS) {
	            costPerSec = Constants.L_PRICE;
	        } else if (mips == Constants.M_MIPS) {
	            costPerSec = Constants.M_PRICE;
	        } else if (mips == Constants.H_MIPS) {
	            costPerSec = Constants.H_PRICE;
	        }

	        // 获取当前云任务分配的虚拟机ID
	        int vmId = cloudletToVm[i];
	        // 计算当前云任务在分配的虚拟机上的执行成本，并累加到总成本中
	        cost += length / vmList.get(vmId).getMips() * costPerSec;
	    }

	    // 返回总成本
	    return cost;
	}

	public double estimateTotalTime(int[] cloudletToVm) {
		double totalTime = 0;
		for (int i = 0; i < cloudletNum; i++) {
			long length = cloudletList.get(i).getCloudletLength();
			int vmId = cloudletToVm[i];
			totalTime += length / vmList.get(vmId).getMips();
		}
		return totalTime;
	}

	private double estimateMaxCost() {
		int[] cloudletToVm = new int[cloudletNum];
		Arrays.fill(cloudletToVm, 0);

		return estimateCost(cloudletToVm);
	}

	private double estimateMinCost() {
		int[] cloudletToVm = new int[cloudletNum];
		Arrays.fill(cloudletToVm, vmNum-1);
		return estimateCost(cloudletToVm);
	}

	private double estimateMaxTotalTime() {
		int[] cloudletToVm = new int[cloudletNum];
		Arrays.fill(cloudletToVm, 0);
		return estimateTotalTime(cloudletToVm);
	}

	private double estimateMinTotalTime() {
		int[] cloudletToVm = new int[cloudletNum];
		Arrays.fill(cloudletToVm, vmNum-1);
		return estimateTotalTime(cloudletToVm);
	}

	private double estimateMaxLB() {
		return estimateLB(randomCloudletToVm);
	}

	private double estimateMinLB() {
		return 0.0;
	}

	/**
	 * 估算适应度值
	 * 适应度值是衡量解决方案优劣的指标，值越小表示方案越优
	 * 本方法综合考虑了成本、总时间和负载均衡(LB)三个因素
	 *
	 * @param cloudletToVm 分配给每个虚拟机的云任务数组
	 * @return 返回适应度值，是成本、总时间和负载均衡的加权和
	 */
	public double estimateFitness(int[] cloudletToVm) {
	    // 成本因素：估算当前分配方案的成本，并标准化到[0,1]区间
	    double costRatio = (estimateCost(cloudletToVm) - estimateMinCost()) / (estimateMaxCost() - estimateMinCost());

	    // 时间因素：估算当前分配方案的总时间，并标准化到[0,1]区间
	    double timeRatio = (estimateTotalTime(cloudletToVm) - estimateMinTotalTime()) / (estimateMaxTotalTime() - estimateMinTotalTime());

	    // 负载均衡因素：估算当前分配方案的负载均衡指标，并标准化到[0,1]区间
	    double lbRatio = (estimateLB(cloudletToVm) - estimateMinLB()) / (estimateMaxLB() - estimateMinLB());

	    // 计算并返回适应度值，是成本、时间和负载均衡的加权和
	    return ALPHA * costRatio + BETA * timeRatio + GAMMA * lbRatio;
	}
}
