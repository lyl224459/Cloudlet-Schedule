package CloudletScheduler.MOOptimizer.mosfoa;

import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;
import CloudletScheduler.MOOptimizer.ParetoArchive;
import org.apache.commons.math3.special.Gamma;

import java.util.Arrays;
import java.util.Random;

/**
 * 多目标向日葵优化算法（Multi-Objective Sunflower Optimization Algorithm, MO-SFOA）
 */
public class MOSunflowerOptimization {
    private final OptFunctionMulti optFunction; // 多目标函数
    private final int N;                        // 种群大小
    private final int MaxFEs;                   // 最大函数评估次数
    private final double lb;                    // 变量下界
    private final double ub;                    // 变量上界
    private final int dim;                      // 决策变量维度

    private ParetoArchive archive;              // Pareto 存档
    private static final Random random = new Random();

    public MOSunflowerOptimization(
            OptFunctionMulti optFunction,
            int N,
            int MaxFEs,
            double lb,
            double ub,
            int dim,
            int archiveMaxSize) {
        this.optFunction = optFunction;
        this.N = N;
        this.MaxFEs = MaxFEs;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.archive = new ParetoArchive(archiveMaxSize);
    }

    /**
     * 初始化种群：在 [lb, ub] 内均匀随机
     */
    private double[][] initialization() {
        double[][] X = new double[N][dim];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < dim; j++) {
                X[i][j] = lb + (ub - lb) * random.nextDouble();
            }
        }
        return X;
    }

    /**
     * Levy 飞行生成（Mantegna 方法）
     */
    private double[][] levy(int size, int dim, double beta) {
        double numerator = Gamma.gamma(1 + beta) * Math.sin(Math.PI * beta / 2);
        double denominator = Gamma.gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2);
        double sigma = Math.pow(numerator / denominator, 1.0 / beta);

        double[][] L = new double[size][dim];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < dim; j++) {
                double u = random.nextGaussian() * sigma;
                double v = random.nextGaussian();
                if (Math.abs(v) < 1e-10) v = 1e-10;
                L[i][j] = u / Math.pow(Math.abs(v), 1.0 / beta);
            }
        }
        return L;
    }

    /**
     * 将实数解转为整数分配方案并评估
     */
    private ObjectiveValues evaluate(double[] sol) {
        int[] assignment = Arrays.stream(sol)
                .mapToInt(x -> (int) Math.round(x))
                .toArray();
        return optFunction.evaluate(assignment);
    }

    /**
     * 边界处理
     */
    private void boundCheck(double[][] population) {
        for (int i = 0; i < population.length; i++) {
            for (int j = 0; j < dim; j++) {
                if (population[i][j] < lb) population[i][j] = lb;
                if (population[i][j] > ub) population[i][j] = ub;
            }
        }
    }

    /**
     * 执行 MO-SFOA 主循环
     *
     * @return Pareto 存档（包含非支配解集）
     */
    public ParetoArchive execute() {
        double[][] X = initialization();
        ObjectiveValues[] fitness = new ObjectiveValues[N];
        int FEs = 0;

        // 初始评估 & 存入存档
        for (int i = 0; i < N; i++) {
            fitness[i] = evaluate(X[i]);
            archive.add(X[i].clone(), fitness[i]);
            FEs++;
            if (FEs >= MaxFEs) break;
        }

        // 主循环
        while (FEs < MaxFEs) {
            double C = 0.8;
            double r1 = random.nextDouble();
            double r2 = random.nextDouble();
            double w = (Math.PI / 2.0) * ((double) FEs / MaxFEs);
            double k = 0.2 * Math.sin(Math.PI / 2.0 - w);
            double[][] levyMat = levy(N, dim, 1.5);
            int y = random.nextInt(N);
            double c1 = random.nextDouble();
            double T = 0.5;
            double m = ((double) FEs / MaxFEs) * 2.0;

            double[] p = new double[dim];
            for (int j = 0; j < dim; j++) {
                p[j] = Math.sin(ub - lb) * 2.0 + (ub - lb) * m;
            }

            double[] Xb = archive.selectLeader(); // 从存档选 leader 作为当前最优引导
            if (Xb == null || Xb.length != dim) {
                // fallback: 用第一个解
                Xb = archive.getSolutions().isEmpty() ? new double[dim] : archive.getSolutions().get(0).clone();
            }

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

            // 边界处理
            boundCheck(Xnew);

            // 评估新种群并更新存档
            for (int i = 0; i < N; i++) {
                ObjectiveValues obj = evaluate(Xnew[i]);
                archive.add(Xnew[i].clone(), obj);
                FEs++;
                if (FEs >= MaxFEs) break;
            }

            // 更新种群（可选：使用存档中的解替换部分个体，此处简化为继续演化）
            X = Xnew;

            if (FEs >= MaxFEs) break;
        }

        System.out.printf("MO-SFOA completed. Pareto archive size = %d%n", archive.size());
        return archive;
    }
}