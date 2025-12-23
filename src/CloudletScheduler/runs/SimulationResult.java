package CloudletScheduler.runs;

public class SimulationResult {
    public final double makespan;
    public final double loadBalance;
    public final double cost;
    public final double totalTime;

    public SimulationResult(double makespan, double loadBalance, double cost, double totalTime) {
        this.makespan = makespan;
        this.loadBalance = loadBalance;
        this.cost = cost;
        this.totalTime = totalTime;
    }
}
