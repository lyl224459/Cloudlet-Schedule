package CloudletScheduler.Optimizer.dbo;

import CloudletScheduler.datacenter.OptFunction;

import java.util.Arrays;
import java.util.Random;

/**
 * Dung Beetle Optimizer (DBO) in Java
 * 改写自 MATLAB 版本，适用于离散调度问题（如 Cloudlet-VM 分配）
 */
public class DungBeetleOptimizer {

    private final OptFunction optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxIter;

    private double[][] x;           // 当前种群位置
    private double[] fit;           // 当前适应度
    private double[][] pX;          // 个体历史最优位置
    private double[] pFit;          // 个体历史最优适应度

    private double fMin;            // 全局最优适应度
    private double[] bestX;         // 全局最优位置

    private double[] convergenceCurve;

    private static final Random random = new Random();

    // DBO 参数
    private static final double P_PERCENT = 0.2; // 生产者比例

    /**
     * 构造函数
     */
    public DungBeetleOptimizer(OptFunction optFunction, int population, double lb, double ub, int dim, int maxIter) {
        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxIter = maxIter;

        this.x = new double[population][dim];
        this.fit = new double[population];
        this.pX = new double[population][dim];
        this.pFit = new double[population];
        this.bestX = new double[dim];
        this.convergenceCurve = new double[maxIter];

        initPopulation();
        System.arraycopy(fit, 0, pFit, 0, population);
        for (int i = 0; i < population; i++) {
            System.arraycopy(x[i], 0, pX[i], 0, dim);
        }

        // 初始化全局最优
        int bestIdx = 0;
        fMin = fit[0];
        for (int i = 1; i < population; i++) {
            if (fit[i] < fMin) {
                fMin = fit[i];
                bestIdx = i;
            }
        }
        System.arraycopy(x[bestIdx], 0, bestX, 0, dim);
    }

    /**
     * 初始化种群
     */
    private void initPopulation() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                x[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            adjustAndEvaluate(i);
        }
    }

    /**
     * 边界处理 + 离散化 + 评估
     */
    private void adjustAndEvaluate(int i) {
        // 离散化为整数（适用于 VM 索引）
        for (int j = 0; j < dim; j++) {
            x[i][j] = Math.round(x[i][j]);
            if (x[i][j] < lb) x[i][j] = lb;
            if (x[i][j] > ub) x[i][j] = ub;
        }
        // 评估
        int[] params = Arrays.stream(x[i]).mapToInt(v -> (int) v).toArray();
        fit[i] = optFunction.calc(params);
    }

    /**
     * 边界约束函数：Bounds(s, Lb, Ub)
     */
    private void bounds(double[] s, double Lb, double Ub) {
        for (int j = 0; j < s.length; j++) {
            if (s[j] < Lb) s[j] = Lb;
            if (s[j] > Ub) s[j] = Ub;
        }
    }
    // 向量边界（每个维度有独立上下界）
    private void bounds(double[] s, double[] Lb, double[] Ub) {
        for (int j = 0; j < s.length; j++) {
            if (s[j] < Lb[j]) s[j] = Lb[j];
            if (s[j] > Ub[j]) s[j] = Ub[j];
        }
    }
    /**
     * 执行 DBO 主循环
     */
    public int[] execute() {
        int pNum = (int) Math.round(population * P_PERCENT); // 生产者数量

        for (int t = 0; t < maxIter; t++) {
            // 找到最差个体
            int worstIdx = 0;
            double fmax = fit[0];
            for (int i = 1; i < population; i++) {
                if (fit[i] > fmax) {
                    fmax = fit[i];
                    worstIdx = i;
                }
            }
            double[] worse = x[worstIdx].clone();

            double r2 = random.nextDouble();
            double R = 1.0 - (double) t / maxIter;

            // ========== 第1组：生产者 (1 to pNum) ==========
            for (int i = 0; i < pNum; i++) {
                if (r2 < 0.9) {
                    double r1 = random.nextDouble();
                    double a = (random.nextDouble() > 0.1) ? 1.0 : -1.0;
                    for (int j = 0; j < dim; j++) {
                        x[i][j] = pX[i][j] + 0.3 * Math.abs(pX[i][j] - worse[j]) + a * 0.1 * pX[i][j];
                    }
                } else {
                    // 随机角度 aaa ∈ [1, 180]
                    int aaa = random.nextInt(180) + 1;
                    if (aaa == 90 || aaa == 180) {
                        // 保持不变（MATLAB 中 aaa==0 不可能，因 randperm(180,1) 返回 1~180）
                        // 所以这里仅处理 90 和 180
                        // 实际上可跳过更新
                    } else {
                        double theta = aaa * Math.PI / 180.0;
                        double tanTheta = Math.tan(theta);
                        for (int j = 0; j < dim; j++) {
                            x[i][j] = pX[i][j] + tanTheta * Math.abs(pX[i][j] - pX[i][j]); // 注意：原式为 XX(i,:)，即 pX
                            // 但 pX[i][j] - pX[i][j] = 0 → 此处应为笔误？
                            // 根据原始论文和常见实现，此处应为 bestX 或其他参考点
                            // 但为忠实于 MATLAB 代码，保留原逻辑（结果恒为 pX[i][j]）
                            // 因此实际上这分支无变化 —— 可能是原文 bug
                            // 我们按 MATLAB 行为：不做改变
                        }
                    }
                }
                bounds(x[i], lb, ub);
                adjustAndEvaluate(i);
            }

            // 当前迭代最优
            int bestII = 0;
            double fMMin = fit[0];
            for (int i = 1; i < population; i++) {
                if (fit[i] < fMMin) {
                    fMMin = fit[i];
                    bestII = i;
                }
            }
            double[] bestXX = x[bestII].clone();

            // ========== 生成新候选解 ==========
            double[] Xnew1 = new double[dim];
            double[] Xnew2 = new double[dim];
            double[] Xnew11 = new double[dim];
            double[] Xnew22 = new double[dim];

            for (int j = 0; j < dim; j++) {
                Xnew1[j] = bestXX[j] * (1 - R);
                Xnew2[j] = bestXX[j] * (1 + R);
                Xnew11[j] = bestX[j] * (1 - R);
                Xnew22[j] = bestX[j] * (1 + R);
            }
            bounds(Xnew1, lb, ub);
            bounds(Xnew2, lb, ub);
            bounds(Xnew11, lb, ub);
            bounds(Xnew22, lb, ub);

            // ========== 第2组：跟随者 (pNum+1 to 12) ==========
            int group2Start = pNum;
            int group2End = Math.min(12, population);
            for (int i = group2Start; i < group2End; i++) {
                for (int j = 0; j < dim; j++) {
                    x[i][j] = bestXX[j] +
                            random.nextDouble() * (pX[i][j] - Xnew1[j]) +
                            random.nextDouble() * (pX[i][j] - Xnew2[j]);
                }
                bounds(x[i], Xnew1, Xnew2); // 注意：此处边界为 Xnew1/Xnew2
                adjustAndEvaluate(i);
            }

            // ========== 第3组：偷窃者 (13 to 19) ==========
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

            // ========== 第4组：滚球蜣螂 (20 to pop) ==========
            int group4Start = 19;
            for (int i = group4Start; i < population; i++) {
                for (int j = 0; j < dim; j++) {
                    double diff1 = Math.abs(pX[i][j] - bestXX[j]);
                    double diff2 = Math.abs(pX[i][j] - bestX[j]);
                    x[i][j] = bestX[j] + random.nextGaussian() * (diff1 + diff2) / 2.0;
                }
                bounds(x[i], lb, ub);
                adjustAndEvaluate(i);
            }

            // ========== 更新个体历史最优和全局最优 ==========
            for (int i = 0; i < population; i++) {
                if (fit[i] < pFit[i]) {
                    pFit[i] = fit[i];
                    System.arraycopy(x[i], 0, pX[i], 0, dim);
                }
                if (pFit[i] < fMin) {
                    fMin = pFit[i];
                    System.arraycopy(pX[i], 0, bestX, 0, dim);
                }
            }

            convergenceCurve[t] = fMin;
        }

        return Arrays.stream(bestX).map(Math::round).mapToInt(v -> (int) v).toArray();
    }

    // ================== Getters ==================

    public double[] getConvergenceCurve() {
        return convergenceCurve;
    }

    public double[] getBestX() {
        return bestX;
    }

    public double getFMin() {
        return fMin;
    }
}