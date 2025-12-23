package CloudletScheduler.Optimizer.iwoa;

import CloudletScheduler.datacenter.OptFunction;

import java.util.Arrays;
import java.util.Random;

import static CloudletScheduler.strategy.chaosMap.tentMap;
import static CloudletScheduler.strategy.mutations.applyGaussianEliteMutation;

/**
 * 改进型鲸鱼优化算法类（IWOA）
 * 融合混沌初始化、高斯精英突变、交叉操作与精英保留机制
 */
public class IWhaleOptimizationAlgorithm {
    private final OptFunction optFunction;
    private double lb, ub;
    private int population;
    private final int dim;
    private final int maxIter;
    private double[][] positions;
    private boolean minimize;
    private double[] convergenceCurve;
    private double[] optimalPos;
    private double optimalScore;
    private double mutationRate = 0.005;
    private static final Random random = new Random();
    private static final int DEGREES_OF_FREEDOM = 5;
    private static final double F = 0.8;

    // 新增：记录每个个体的适应度值，用于精英保留
    private double[] fitnessValues;

    /**
     * 构造函数
     */
    public IWhaleOptimizationAlgorithm(OptFunction optFunction, int population, double lb, double ub, int dim, int maxIter, boolean minimize) {
        if (lb >= ub) throw new IllegalArgumentException("lb must be less than ub");
        if (dim <= 0 || population <= 0 || maxIter <= 0)
            throw new IllegalArgumentException("Invalid parameters: dim, population, and maxIter must be positive.");

        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxIter = maxIter;
        this.minimize = minimize;
        this.optimalScore = minimize ? Double.MAX_VALUE : -Double.MAX_VALUE;
        this.optimalPos = new double[dim];
        this.positions = new double[population][dim];
        this.convergenceCurve = new double[maxIter];
        this.fitnessValues = new double[population]; // 初始化适应度数组

        initPopulation();
    }

    private void adjustPositions(int agentIndex) {
        for (int j = 0; j < dim; j++) {
            positions[agentIndex][j] = Math.round(positions[agentIndex][j]);
            if (positions[agentIndex][j] < lb) positions[agentIndex][j] = lb;
            if (positions[agentIndex][j] > ub) positions[agentIndex][j] = ub;
        }
    }

    private void initPopulation() {
        for (int i = 0; i < population; i++) {
            double x = random.nextDouble(); // 初始混沌种子
            for (int j = 0; j < dim; j++) {
                // 使用 Tent 混沌映射生成多样性初始值（已启用）
                x = tentMap(x);
                positions[i][j] = lb + (ub - lb) * x;
            }
            adjustPositions(i);
        }
    }

    private void calcFitness() {
        for (int i = 0; i < population; i++) {
            adjustPositions(i);
            int[] params = Arrays.stream(positions[i]).mapToInt(x -> (int) Math.round(x)).toArray();
            double fitness = optFunction.calc(params);
            fitnessValues[i] = fitness;

            if (minimize && fitness < optimalScore || !minimize && fitness > optimalScore) {
                optimalScore = fitness;
                System.arraycopy(positions[i], 0, optimalPos, 0, dim);
            }
        }
    }

    private void crossover(double[] parent1, double[] parent2, double[] offspring) {
        int crossoverPoint = random.nextInt(dim);
        for (int i = 0; i < dim; i++) {
            if (i < crossoverPoint) {
                offspring[i] = parent1[i];
            } else {
                offspring[i] = parent2[i];
            }
        }
    }

    private void updatePosition(double a, double a2) {
        for (int i = 1; i < population; i++) {
            double r1 = random.nextDouble();
            double r2 = random.nextDouble();
            double A = 2.0 * a * r1 - a;
            double C = 2.0 * r2;
            double b = 1.0;
            double l = (a2 - 1.0) * random.nextDouble() + 1.0;
            double p = random.nextDouble();

            // 交叉操作（以50%概率）
            if (random.nextDouble() < 0.5) {
                int parent1Index = random.nextInt(population);
                int parent2Index = random.nextInt(population);
                double[] parent1 = positions[parent1Index];
                double[] parent2 = positions[parent2Index];
                double[] offspring = new double[dim];
                crossover(parent1, parent2, offspring);
                System.arraycopy(offspring, 0, positions[i], 0, dim);
            }

            // WOA 核心位置更新
            for (int j = 0; j < dim; j++) {
                if (p < 0.5) {
                    if (Math.abs(A) < 1) {
                        double D_Leader = Math.abs(C * optimalPos[j] - positions[i][j]);
                        positions[i][j] = optimalPos[j] - A * D_Leader;
                    } else {
                        int randWhaleIdx = random.nextInt(population);
                        double[] randomPos = positions[randWhaleIdx];
                        double D_X_rand = Math.abs(C * randomPos[j] - positions[i][j]);
                        positions[i][j] = randomPos[j] - A * D_X_rand;
                    }
                } else {
                    double distance2Leader = Math.abs(optimalPos[j] - positions[i][j]);
                    positions[i][j] = distance2Leader * Math.exp(b * l) * Math.cos(2.0 * Math.PI * l) + optimalPos[j];
                }
            }

            // 高斯精英变异
            applyGaussianEliteMutation(dim, i, optimalPos, positions, mutationRate, lb, ub);
        }
    }

    /**
     * 精英保留：将种群中最差个体替换为当前全局最优个体
     */
    private void replaceWorstWithBest() {
        int worstIdx = 0;
        double worstFitness = fitnessValues[0];

        for (int i = 1; i < population; i++) {
            if (minimize) {
                if (fitnessValues[i] > worstFitness) {
                    worstFitness = fitnessValues[i];
                    worstIdx = i;
                }
            } else {
                if (fitnessValues[i] < worstFitness) {
                    worstFitness = fitnessValues[i];
                    worstIdx = i;
                }
            }
        }

        // 替换最差个体
        System.arraycopy(optimalPos, 0, positions[worstIdx], 0, dim);
        fitnessValues[worstIdx] = optimalScore; // 同步适应度值
    }

    public int[] execute() {
        for (int iter = 0; iter < maxIter; iter++) {
            calcFitness();
            convergenceCurve[iter] = optimalScore;

            double a = 2.0 - (double) iter * (2.0 / maxIter);
            double a2 = -1.0 + (double) iter * (-1.0 / maxIter);

            updatePosition(a, a2);

            // 👇 精英保留机制
            replaceWorstWithBest();
        }

        calcFitness(); // 确保最终结果是最新的
        return Arrays.stream(optimalPos).map(Math::round).mapToInt(x -> (int) x).toArray();
    }

    // Getter 方法
    public double[] getConvergenceCurve() {
        return convergenceCurve;
    }

    public double[] getLeaderPos() {
        return optimalPos;
    }

    public double getOptimalScore() {
        return optimalScore;
    }

    public double[][] getPositions() {
        return positions;
    }
}