package CloudletScheduler.runs;

import CloudletScheduler.MOOptimizer.ParetoArchive;

public class SimulationResult {
    public final double makespan;
    public final double loadBalance;
    public final double cost;
    public final double totalTime;
    private ParetoArchive paretoArchive;
    private ParetoArchive firstGenerationArchive;

    public SimulationResult(double makespan, double loadBalance, double cost, double totalTime) {
        this.makespan = makespan;
        this.loadBalance = loadBalance;
        this.cost = cost;
        this.totalTime = totalTime;
    }
    
    public void setParetoArchive(ParetoArchive archive) {
        this.paretoArchive = archive;
    }
    
    public ParetoArchive getParetoArchive() {
        return paretoArchive;
    }
    
    public void setFirstGenerationArchive(ParetoArchive archive) {
        this.firstGenerationArchive = archive;
    }
    
    public ParetoArchive getFirstGenerationArchive() {
        return firstGenerationArchive;
    }
    
    public boolean hasParetoArchive() {
        return paretoArchive != null && !paretoArchive.isEmpty();
    }
    
    public boolean hasFirstGenerationArchive() {
        return firstGenerationArchive != null && !firstGenerationArchive.isEmpty();
    }
}
