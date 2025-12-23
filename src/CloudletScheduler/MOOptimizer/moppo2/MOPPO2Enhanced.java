package CloudletScheduler.MOOptimizer.moppo2;

import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.special.Gamma;

import java.util.Arrays;
import java.util.Random;

public class MOPPO2Enhanced {

    private final OptFunctionMulti optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxFEs;

    private final double[][] positions;
    private final double[][] flockMemoryX;
    private final ObjectiveValues[] flockMemoryF;
    private final ParetoArchive archive;
    private int evaluations;

    private static final Random random = new Random();
    private final NormalDistribution normal = new NormalDistribution();

    public MOPPO2Enhanced(OptFunctionMulti optFunction,
                          int population,
                          double lb,
                          double ub,
                          int dim,
                          int maxFEs,
                          int archiveMaxSize) {
        if (lb >= ub) throw new IllegalArgumentException("Lower bound must be < upper bound.");
        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxFEs = maxFEs;

        this.positions = new double[population][dim];
        this.flockMemoryX = new double[population][dim];
        this.flockMemoryF = new ObjectiveValues[population];
        this.archive = new ParetoArchive(archiveMaxSize);
        this.evaluations = 0;

        initializePopulation();
    }

    private void initializePopulation() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) positions[i][j] = lb + (ub - lb) * random.nextDouble();
            roundAndClamp(positions[i]);
            evaluateAndArchive(i);
        }
        for (int i = 0; i < population; i++) {
            flockMemoryX[i] = positions[i].clone();
            flockMemoryF[i] = evaluate(positions[i]);
        }
    }

    private void roundAndClamp(double[] pos) {
        for (int j = 0; j < dim; j++) {
            pos[j] = Math.round(pos[j]);
            if (pos[j] < lb) pos[j] = lb;
            if (pos[j] > ub) pos[j] = ub;
        }
    }

    private ObjectiveValues evaluate(double[] pos) {
        int[] params = Arrays.stream(pos).mapToInt(x -> (int) x).toArray();
        return optFunction.evaluate(params);
    }

    private void evaluateAndArchive(int i) {
        if (evaluations >= maxFEs) return;
        ObjectiveValues obj = evaluate(positions[i]);
        archive.add(positions[i], obj);
        evaluations++;
    }

    private double[] levyFlight(int d, double beta) {
        double sigma = Math.pow(
                Gamma.gamma(1 + beta) * Math.sin(Math.PI * beta / 2) /
                        (Gamma.gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2)),
                1.0 / beta);
        double[] step = new double[d];
        for (int i = 0; i < d; i++) {
            double u = normal.sample() * sigma;
            double v = Math.abs(normal.sample());
            step[i] = u / Math.pow(v, 1.0 / beta);
        }
        return step;
    }

    private double[] deMutationAdaptive(int target, double fitness) {
        int r1, r2, r3;
        do { r1 = random.nextInt(population); } while (r1 == target);
        do { r2 = random.nextInt(population); } while (r2 == target || r2 == r1);
        do { r3 = random.nextInt(population); } while (r3 == target || r3 == r1 || r3 == r2);

        double F = 0.4 + 0.4 * random.nextDouble(); // 自适应 F
        if (fitness < 0.5) F += 0.1; // 差异较大时加强搜索
        double[] v = new double[dim];
        for (int j = 0; j < dim; j++) {
            v[j] = positions[r1][j] + F * (positions[r2][j] - positions[r3][j]);
        }
        roundAndClamp(v);
        return v;
    }

    private double[] computePseudoFitness() {
        double[] fitness = new double[population];
        var archiveObjs = archive.getObjectives();
        if (archiveObjs.isEmpty()) { Arrays.fill(fitness, 1.0); return fitness; }
        for (int i = 0; i < population; i++) {
            double minDist = Double.MAX_VALUE;
            ObjectiveValues obj = evaluate(positions[i]);
            for (ObjectiveValues aObj : archiveObjs)
                minDist = Math.min(minDist, euclideanDistance(obj.values, aObj.values));
            fitness[i] = 1.0 / (minDist + 1e-9);
        }
        return fitness;
    }

    private double euclideanDistance(double[] a, double[] b) {
        double s = 0; for (int i = 0; i < a.length; i++) s += (a[i]-b[i])*(a[i]-b[i]); return Math.sqrt(s);
    }

    private boolean isDominated(ObjectiveValues a, ObjectiveValues b) {
        if (b==null) return false; boolean worse=false;
        for(int i=0;i<a.values.length;i++){if(a.values[i]<b.values[i]) return false; if(a.values[i]>b.values[i]) worse=true;}
        return worse;
    }

    private void enforceBounds() { for(int i=0;i<population;i++) roundAndClamp(positions[i]); }

    public ParetoArchive execute() {
        while (evaluations < maxFEs) {
            double t = (double)evaluations/maxFEs;
            double[] pseudoF = computePseudoFitness();
            double maxF = Arrays.stream(pseudoF).max().orElse(1.0);

            for(int i=0;i<population;i++){
                double fitness = pseudoF[i]/(maxF+1e-9);
                boolean usePredator = random.nextDouble() > (0.85 - 0.7*t*fitness);
                double beta = 1.5 - 0.5*t*(1.0-fitness); // 多样性低时增加探索

                if(usePredator){
                    double[] leader = archive.selectLeader();
                    if(leader==null || leader.length!=dim){
                        var sols = archive.getSolutions();
                        leader = sols.isEmpty()?positions[random.nextInt(population)]:sols.get(random.nextInt(sols.size()));
                    }
                    for(int j=0;j<dim;j++)
                        positions[i][j]=leader[j]+Math.cos(random.nextDouble()*Math.PI)*(positions[i][j]-leader[j]);
                } else {
                    double[] levy = levyFlight(dim,beta);
                    for(int j=0;j<dim;j++) positions[i][j]+=levy[j]*fitness;
                }

                // 自适应 DE/rand/1
                if(random.nextDouble()<0.35){
                    double[] mutant = deMutationAdaptive(i, fitness);
                    for(int j=0;j<dim;j++)
                        positions[i][j]=0.65*positions[i][j]+0.35*mutant[j];
                }

                roundAndClamp(positions[i]);
            }

            // 更新 archive + memory
            for(int i=0;i<population && evaluations<maxFEs;i++) evaluateAndArchive(i);
            for(int i=0;i<population;i++){
                ObjectiveValues cur = evaluate(positions[i]);
                if(!isDominated(cur,flockMemoryF[i])){
                    flockMemoryF[i]=cur; flockMemoryX[i]=positions[i].clone();
                }
            }
        }
        return archive;
    }

    public ParetoArchive getArchive(){ return archive; }
}
