package CloudletScheduler.Optimizer.sfoa;

import CloudletScheduler.datacenter.OptFunction;
import org.apache.commons.math3.special.Gamma;

import java.util.Arrays;
import java.util.Random;

/**
 * 向日葵优化算法（Sunflower Optimization Algorithm, SFOA）
 */
public class SunflowerOptimization {
    private final OptFunction optFunction; // 目标函数
    private final int N;                   // 种群大小
    private final int MaxFEs;              // 最大函数评估次数
    private final double lb;               // 变量下界
    private final double ub;               // 变量上界
    private final int dim;                 // 决策变量维度

    private double bestFitness;
    private double[] bestPosition;
    private double[] curve;                // 收敛曲线（长度 = MaxFEs）

    private static final Random random = new Random();

    public SunflowerOptimization(OptFunction optFunction, int N, int MaxFEs, double lb, double ub, int dim) {
        this.optFunction = optFunction;
        this.N = N;
        this.MaxFEs = MaxFEs;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;

        this.bestFitness = Double.POSITIVE_INFINITY;
        this.bestPosition = new double[dim];
        this.curve = new double[MaxFEs];
    }

    /**
     * 初始化种群：在 [lb, ub] 内均匀随机
     */
    private double[][] initialization(int popSize, int dim, double ub, double lb) {
        double[][] X = new double[popSize][dim];
        for (int i = 0; i < popSize; i++) {
            for (int j = 0; j < dim; j++) {
                X[i][j] = lb + (ub - lb) * random.nextDouble();
            }
        }
        return X;
    }

    /**
     * Levy 飞行生成（使用 Mantegna 方法近似）
     * @param size 行数
     * @param dim 列数
     * @param beta Levy 指数（通常 1.5）
     * @return Levy 步长矩阵
     */
    private double[][] levy(int size, int dim, double beta) {
        // 计算 sigma 使用 Gamma.gamma()
        double numerator = Gamma.gamma(1 + beta) * Math.sin(Math.PI * beta / 2);
        double denominator = Gamma.gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2);
        double sigma = Math.pow(numerator / denominator, 1.0 / beta);

        double[][] L = new double[size][dim];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < dim; j++) {
                double u = random.nextGaussian() * sigma;
                double v = random.nextGaussian();
                // 避免除零或 NaN
                if (Math.abs(v) < 1e-10) {
                    v = 1e-10;
                }
                L[i][j] = u / Math.pow(Math.abs(v), 1.0 / beta);
            }
        }
        return L;
    }

    /**
     * 评估个体（转为整数后调用目标函数）
     */
    private double evaluate(double[] sol) {
        int[] params = Arrays.stream(sol).mapToInt(x -> (int) Math.round(x)).toArray();
        return optFunction.calc(params);
    }

    /**
     * 执行 SFOA 主循环
     *
     * @return 最优整数解（VM 分配方案）
     */
    public int[] execute() {
        double[][] X = initialization(N, dim, ub, lb);
        double[] fitness = new double[N];
        int FEs = 0;

        // 初始评估
        for (int i = 0; i < N; i++) {
            fitness[i] = evaluate(X[i]);
            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                System.arraycopy(X[i], 0, bestPosition, 0, dim);
            }
            FEs++;
            if (FEs <= MaxFEs) curve[FEs - 1] = bestFitness;
        }

        // 主循环
        while (FEs < MaxFEs) {
            double C = 0.8;
            double r1 = random.nextDouble();
            double r2 = random.nextDouble();
            double w = (Math.PI / 2.0) * ((double) FEs / MaxFEs);
            double k = 0.2 * Math.sin(Math.PI / 2.0 - w);
            double[][] levyMat = levy(N, dim, 1.5); // 预生成 Levy 矩阵
            int y = random.nextInt(N); // MATLAB randi(N) → [1,N]，Java nextInt(N) → [0,N-1]
            double c1 = random.nextDouble();
            double T = 0.5;
            double m = ((double) FEs / MaxFEs) * 2.0;
            double[] p = new double[dim];
            for (int j = 0; j < dim; j++) {
                p[j] = Math.sin(ub - lb) * 2.0 + (ub - lb) * m;
            }

            double[] Xb = bestPosition.clone();
            double[] XG = new double[dim];
            for (int j = 0; j < dim; j++) {
                XG[j] = Xb[j] * C;
            }

            double[][] Xnew = new double[N][dim];

            // 更新每个个体
            for (int i = 0; i < N; i++) {
                if (T < c1) {
                    // 分支1：随机探索
                    for (int j = 0; j < dim; j++) {
                        Xnew[i][j] = X[i][j] + (lb + (ub - lb) * random.nextDouble());
                    }
                } else {
                    double s = r1 * 20 + r2 * 20;
                    if (s > 20) {
                        // 分支2a：Levy 飞行扰动
                        for (int j = 0; j < dim; j++) {
                            Xnew[i][j] = Xb[j] + X[i][j] * levyMat[y][j] * k;
                        }
                    } else {
                        // 分支2b：向全局引导点移动
                        for (int j = 0; j < dim; j++) {
                            Xnew[i][j] = XG[j] + (Xb[j] - X[i][j]) * p[j];
                        }
                    }
                }
            }

            // 评估新种群
            for (int i = 0; i < N; i++) {
                // 边界处理
                for (int j = 0; j < dim; j++) {
                    if (Xnew[i][j] < lb) Xnew[i][j] = lb;
                    if (Xnew[i][j] > ub) Xnew[i][j] = ub;
                }

                fitness[i] = evaluate(Xnew[i]);
                if (fitness[i] < bestFitness) {
                    bestFitness = fitness[i];
                    System.arraycopy(Xnew[i], 0, bestPosition, 0, dim);
                }

                FEs++;
                if (FEs <= MaxFEs) {
                    curve[FEs - 1] = bestFitness;
                }

                if (FEs >= MaxFEs) break;
            }

            // 更新种群
            X = Xnew;

            if (FEs >= MaxFEs) break;
        }

        // 填充剩余 curve（若未满）
        for (int i = FEs; i < MaxFEs; i++) {
            curve[i] = bestFitness;
        }

        System.out.printf("SFOA completed. Best Fitness = %.10f%n", bestFitness);

        // 返回整数解（VM ID）
        return Arrays.stream(bestPosition)
                .mapToLong(Math::round)
                .mapToInt(l -> (int) l)
                .toArray();
    }

    // Getter
    public double[] getConvergenceCurve() {
        return curve;
    }

    public double[] getBestPosition() {
        return bestPosition;
    }

    public double getBestFitness() {
        return bestFitness;
    }
}