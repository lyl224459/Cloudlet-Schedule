package CloudletScheduler.runs;

import org.cloudbus.cloudsim.Cloudlet;

import java.util.List;

public class ResultAnalyzer {

    public static SimulationResult analyzeResults(List<Cloudlet> cloudletList, int vmCount) {
        double makespan = 0;
        double[] executeTimeOfVM = new double[vmCount];
        double totalCost = 0;
        double totalTime = 0;

        for (Cloudlet cloudlet : cloudletList) {
            if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
                double finishTime = cloudlet.getFinishTime();
                makespan = Math.max(makespan, finishTime);

                int vmId = cloudlet.getVmId();
                double actualCPUTime = cloudlet.getActualCPUTime();
                executeTimeOfVM[vmId] += actualCPUTime;
                totalTime += actualCPUTime;
                totalCost += actualCPUTime * cloudlet.getCostPerSec();
            }
        }

        double avg = java.util.Arrays.stream(executeTimeOfVM).average().orElse(0.0);
        double lb = Math.sqrt(java.util.Arrays.stream(executeTimeOfVM).map(x -> Math.pow(x - avg, 2)).average().orElse(0.0));

        return new SimulationResult(makespan, lb, totalCost, totalTime);
    }
}
