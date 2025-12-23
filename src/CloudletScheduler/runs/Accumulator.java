package CloudletScheduler.runs;

public class Accumulator {
    public double totalMakespan = 0;
    public double totalLoadBalance = 0;
    public double totalCost = 0;
    public double totalTotalTime = 0;

    public void add(SimulationResult r) {
        totalMakespan += r.makespan;
        totalLoadBalance += r.loadBalance;
        totalCost += r.cost;
        totalTotalTime += r.totalTime;
    }
}
