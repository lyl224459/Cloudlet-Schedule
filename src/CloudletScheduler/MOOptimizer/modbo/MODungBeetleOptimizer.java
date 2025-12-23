package CloudletScheduler.MOOptimizer.modbo;

import CloudletScheduler.MOOptimizer.ParetoArchive;
import CloudletScheduler.datacenter.ObjectiveValues;
import CloudletScheduler.datacenter.OptFunctionMulti;

import java.util.Arrays;
import java.util.Random;

/**
 * Multi-Objective Dung Beetle Optimizer (MO-DBO)
 * 支持 Pareto 优化，适用于离散多目标调度问题
 */
public class MODungBeetleOptimizer {

    private final OptFunctionMulti optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxIter;

    private double[][] x;                    // 当前种群位置
    private ObjectiveValues[] pObj;          // 个体历史最优目标值
    private double[][] pX;                   // 个体历史最优位置

    private ParetoArchive archive;           // 外部存档（非支配解集）

    private static final Random random = new Random();
    private static final double P_PERCENT = 0.2;

    public MODungBeetleOptimizer(
            OptFunctionMulti optFunction,
            int population,
            double lb,
            double ub,
            int dim,
            int maxIter,
            int archiveMaxSize) {

        if (lb >= ub) throw new IllegalArgumentException("lb must be < ub");
        if (population <= 0 || dim <= 0 || maxIter <= 0)
            throw new IllegalArgumentException("Invalid parameters");

        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxIter = maxIter;

        this.x = new double[population][dim];
        this.pX = new double[population][dim];
        this.pObj = new ObjectiveValues[population];
        this.archive = new ParetoArchive(archiveMaxSize);

        initPopulation();
    }

    private void initPopulation() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                x[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            adjustPosition(i);
            int[] assignment = Arrays.stream(x[i]).mapToInt(v -> (int) v).toArray();
            ObjectiveValues obj = optFunction.evaluate(assignment);

            System.arraycopy(x[i], 0, pX[i], 0, dim);
            pObj[i] = obj.clone(); // 确保 ObjectiveValues 实现了 clone()

            archive.add(x[i], obj);
        }
    }

    private void adjustPosition(int i) {
        for (int j = 0; j < dim; j++) {
            x[i][j] = Math.round(x[i][j]);
            if (x[i][j] < lb) x[i][j] = lb;
            if (x[i][j] > ub) x[i][j] = ub;
        }
    }

    private void adjustAndEvaluate(int i) {
        adjustPosition(i);
        int[] assignment = Arrays.stream(x[i]).mapToInt(v -> (int) v).toArray();
        ObjectiveValues obj = optFunction.evaluate(assignment);
        archive.add(x[i], obj);

        // 如果当前解不被个体历史最优支配，则更新历史最优
        if (pObj[i] == null || !isDominatedBy(obj, pObj[i])) {
            pObj[i] = obj.clone();
            System.arraycopy(x[i], 0, pX[i], 0, dim);
        }
    }

    /**
     * 判断 candidate 是否被 existing 支配
     */
    private boolean isDominatedBy(ObjectiveValues candidate, ObjectiveValues existing) {
        boolean betterInOne = false;
        for (int k = 0; k < candidate.values.length; k++) {
            if (existing.values[k] > candidate.values[k]) {
                return false; // existing 更差 → 不支配 candidate
            }
            if (existing.values[k] < candidate.values[k]) {
                betterInOne = true;
            }
        }
        return betterInOne; // existing 全部 ≤ candidate 且至少一个 < → 支配
    }

    private void bounds(double[] s, double Lb, double Ub) {
        for (int j = 0; j < s.length; j++) {
            if (s[j] < Lb) s[j] = Lb;
            if (s[j] > Ub) s[j] = Ub;
        }
    }

    private void bounds(double[] s, double[] Lb, double[] Ub) {
        for (int j = 0; j < s.length; j++) {
            double low = Math.min(Lb[j], Ub[j]);
            double high = Math.max(Lb[j], Ub[j]);
            if (s[j] < low) s[j] = low;
            if (s[j] > high) s[j] = high;
        }
    }

    public ParetoArchive execute() {
        int pNum = (int) Math.round(population * P_PERCENT);

        for (int t = 0; t < maxIter; t++) {
            int worstIdx = random.nextInt(population);
            double[] worse = x[worstIdx].clone();

            double r2 = random.nextDouble();
            double R = 1.0 - (double) t / maxIter;

            // ========== 第1组：生产者 ==========
            for (int i = 0; i < pNum; i++) {
                if (r2 < 0.9) {
                    double a = (random.nextDouble() > 0.1) ? 1.0 : -1.0;
                    for (int j = 0; j < dim; j++) {
                        x[i][j] = pX[i][j] + 0.3 * Math.abs(pX[i][j] - worse[j]) + a * 0.1 * pX[i][j];
                    }
                } else {
                    double[] leader = archive.selectLeader();
                    if (leader != null) {
                        System.arraycopy(leader, 0, x[i], 0, dim);
                    }
                }
                bounds(x[i], lb, ub);
                adjustAndEvaluate(i);
            }

            // 获取引导解
            double[] bestXX = archive.selectLeader();
            if (bestXX == null) {
                bestXX = x[0].clone();
            }

            double[] globalBest = archive.getBestBySum();
            if (globalBest == null) {
                globalBest = bestXX;
            }

            // ========== 构造新候选解 ==========
            double[] Xnew1 = new double[dim];
            double[] Xnew2 = new double[dim];
            double[] Xnew11 = new double[dim];
            double[] Xnew22 = new double[dim];

            for (int j = 0; j < dim; j++) {
                Xnew1[j] = bestXX[j] * (1 - R);
                Xnew2[j] = bestXX[j] * (1 + R);
                Xnew11[j] = globalBest[j] * (1 - R);
                Xnew22[j] = globalBest[j] * (1 + R);
            }
            bounds(Xnew1, lb, ub);
            bounds(Xnew2, lb, ub);
            bounds(Xnew11, lb, ub);
            bounds(Xnew22, lb, ub);

            // ========== 第2组：跟随者 ==========
            int group2Start = pNum;
            int group2End = Math.min(12, population);
            for (int i = group2Start; i < group2End; i++) {
                for (int j = 0; j < dim; j++) {
                    x[i][j] = bestXX[j] +
                            random.nextDouble() * (pX[i][j] - Xnew1[j]) +
                            random.nextDouble() * (pX[i][j] - Xnew2[j]);
                }
                bounds(x[i], Xnew1, Xnew2);
                adjustAndEvaluate(i);
            }

            // ========== 第3组：偷窃者 ==========
            int group3Start = 12;
            int group3End = Math.min(19, population);
            for (int i = group3Start; i < group3End; i++) {
                double randn = random.nextGaussian();
                for (int j = 0; j < dim; j++) {
                    x[i][j] = pX[i][j] +
                            randn * (pX[i][j] - Xnew11[j]) +
                            random.nextDouble() * (pX[i][j] - Xnew22[j]);
                }
                bounds(x[i], lb, ub);
                adjustAndEvaluate(i);
            }

            // ========== 第4组：滚球蜣螂 ==========
            int group4Start = 19;
            for (int i = group4Start; i < population; i++) {
                for (int j = 0; j < dim; j++) {
                    double diff1 = Math.abs(pX[i][j] - bestXX[j]);
                    double diff2 = Math.abs(pX[i][j] - globalBest[j]);
                    x[i][j] = globalBest[j] + random.nextGaussian() * (diff1 + diff2) / 2.0;
                }
                bounds(x[i], lb, ub);
                adjustAndEvaluate(i);
            }
        }

        return archive;
    }
}