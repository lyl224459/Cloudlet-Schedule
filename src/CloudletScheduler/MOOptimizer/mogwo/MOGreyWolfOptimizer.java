package CloudletScheduler.MOOptimizer.mogwo;

import CloudletScheduler.MOOptimizer.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;

import java.util.Arrays;
import java.util.Random;

/**
 * Multi-Objective Grey Wolf Optimizer (MO-GWO)
 * 使用 Pareto 存档维护非支配解，模拟 α/β/δ 引导机制。
 */
public class MOGreyWolfOptimizer {

    private final OptFunctionMulti optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxIter;
    private final int archiveMaxSize;

    private double[][] positions;
    private ParetoArchive archive;
    private ParetoArchive firstGenerationArchive; // 第一代Pareto存档快照

    private static final Random random = new Random();

    public MOGreyWolfOptimizer(
            OptFunctionMulti optFunction,
            int population,
            double lb,
            double ub,
            int dim,
            int maxIter,
            int archiveMaxSize) {

        if (lb >= ub) throw new IllegalArgumentException("Lower bound must be less than upper bound.");
        if (dim <= 0 || population <= 0 || maxIter <= 0 || archiveMaxSize <= 0)
            throw new IllegalArgumentException("Invalid algorithm parameters.");

        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxIter = maxIter;
        this.archiveMaxSize = archiveMaxSize;
        this.positions = new double[population][dim];
        this.archive = new ParetoArchive(archiveMaxSize);

        initializePositions();
    }

    private void initializePositions() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                positions[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            adjustPosition(i);
            int[] assignment = Arrays.stream(positions[i]).mapToInt(v -> (int) v).toArray();
            ObjectiveValues obj = optFunction.evaluate(assignment);
            archive.add(positions[i], obj);
        }
        
        // 保存第一代Pareto存档快照（初始化完成后）
        firstGenerationArchive = archive.deepCopy();
    }
    
    /**
     * 获取第一代Pareto存档快照
     * @return 第一代Pareto存档
     */
    public ParetoArchive getFirstGenerationArchive() {
        return firstGenerationArchive;
    }

    private void adjustPosition(int idx) {
        for (int j = 0; j < dim; j++) {
            positions[idx][j] = Math.round(positions[idx][j]);
            if (positions[idx][j] < lb) positions[idx][j] = lb;
            if (positions[idx][j] > ub) positions[idx][j] = ub;
        }
    }

    private double[][] selectThreeLeaders() {
        double[][] leaders = new double[3][dim];
        var solutions = archive.getSolutions();

        if (solutions.isEmpty()) {
            // Fallback: use random individuals from current population
            for (int i = 0; i < 3; i++) {
                System.arraycopy(positions[random.nextInt(population)], 0, leaders[i], 0, dim);
            }
            return leaders;
        }

        // Select up to 3 leaders via archive's leader selection (e.g., random or crowding distance)
        for (int i = 0; i < 3; i++) {
            double[] candidate = archive.selectLeader();
            if (candidate != null && candidate.length == dim) {
                leaders[i] = candidate.clone();
            } else {
                // If selectLeader fails, cycle through archive
                leaders[i] = solutions.get(Math.min(i, solutions.size() - 1)).clone();
            }
        }
        return leaders;
    }

    public ParetoArchive execute() {
        for (int iter = 0; iter < maxIter; iter++) {
            double a = 2.0 - (2.0 * iter) / maxIter; // a decreases linearly from 2 to 0

            double[][] leaders = selectThreeLeaders();
            double[] alpha = leaders[0];
            double[] beta = leaders[1];
            double[] delta = leaders[2];

            for (int i = 0; i < population; i++) {
                for (int j = 0; j < dim; j++) {
                    // Update towards Alpha
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();
                    double A1 = 2 * a * r1 - a;
                    double C1 = 2 * r2;
                    double D_alpha = Math.abs(C1 * alpha[j] - positions[i][j]);
                    double X1 = alpha[j] - A1 * D_alpha;

                    // Update towards Beta
                    r1 = random.nextDouble();
                    r2 = random.nextDouble();
                    double A2 = 2 * a * r1 - a;
                    double C2 = 2 * r2;
                    double D_beta = Math.abs(C2 * beta[j] - positions[i][j]);
                    double X2 = beta[j] - A2 * D_beta;

                    // Update towards Delta
                    r1 = random.nextDouble();
                    r2 = random.nextDouble();
                    double A3 = 2 * a * r1 - a;
                    double C3 = 2 * r2;
                    double D_delta = Math.abs(C3 * delta[j] - positions[i][j]);
                    double X3 = delta[j] - A3 * D_delta;

                    // New position is average of three directions
                    positions[i][j] = (X1 + X2 + X3) / 3.0;

                    // Boundary handling (floating point)
                    if (positions[i][j] < lb) positions[i][j] = lb;
                    if (positions[i][j] > ub) positions[i][j] = ub;
                }

                // Discretize and evaluate
                adjustPosition(i);
                int[] assignment = Arrays.stream(positions[i]).mapToInt(v -> (int) v).toArray();
                ObjectiveValues obj = optFunction.evaluate(assignment);
                archive.add(positions[i], obj);
            }
        }

        return archive;
    }

    public ParetoArchive getArchive() {
        return archive;
    }
}