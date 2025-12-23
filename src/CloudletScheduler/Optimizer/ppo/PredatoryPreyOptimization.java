package CloudletScheduler.Optimizer.ppo;

import CloudletScheduler.datacenter.OptFunction;

import java.util.Arrays;
import java.util.Random;

/**
 * 掠食-猎物优化算法（Predatory Prey Optimization, PPO）
 * 模拟生物交配、性食同类与捕食行为的元启发式优化算法
 */
public class PredatoryPreyOptimization {
    // 优化目标函数接口
    private final OptFunction optFunction;
    // 搜索空间下界和上界
    private double lb, ub;
    // 种群大小（个体数量）
    private int population;
    // 问题维度
    private final int dim;
    // 最大函数评估次数（代替最大迭代次数）
    private final int maxFEs;
    // 当前种群位置（X）
    private double[][] positions;
    // 个体历史最优位置记忆（Flock Memory）
    private double[][] flockMemoryX;
    // 个体历史最优适应度记忆
    private double[] flockMemoryF;
    // 收敛曲线：记录每次评估后的全局最优值
    private double[] convergenceCurve;
    // 全局最优解位置
    private double[] bestPos;
    // 全局最优适应度值
    private double bestScore;
    // 已执行的函数评估次数
    private int evaluations;
    // 随机数生成器
    private static final Random random = new Random();

    /**
     * 构造函数：初始化PPO算法参数
     *
     * @param optFunction 目标优化函数
     * @param population  种群大小
     * @param lb          搜索下界
     * @param ub          搜索上界
     * @param dim         问题维度
     * @param maxFEs      最大函数评估次数
     */
    public PredatoryPreyOptimization(OptFunction optFunction, int population, double lb, double ub, int dim, int maxFEs) {
        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxFEs = maxFEs;
        this.positions = new double[population][dim];
        this.flockMemoryX = new double[population][dim];
        this.flockMemoryF = new double[population];
        this.convergenceCurve = new double[maxFEs];
        this.bestScore = Double.POSITIVE_INFINITY; // 最小化问题
        this.bestPos = new double[dim];
        this.evaluations = 0;

        initializePopulation();
    }

    /**
     * 初始化种群：在搜索空间内随机生成初始解，并进行首次评估
     */
    private void initializePopulation() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                positions[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            evaluateIndividual(i); // 评估并更新全局最优
        }
        // 初始化个体记忆：当前即为历史最优
        for (int i = 0; i < population; i++) {
            System.arraycopy(positions[i], 0, flockMemoryX[i], 0, dim);
            flockMemoryF[i] = evaluate(positions[i]);
        }
    }

    /**
     * 计算单个解的适应度值（调用用户定义的目标函数）
     *
     * @param pos 解的位置（实数向量）
     * @return 适应度值
     */
    private double evaluate(double[] pos) {
        // 转换为整数参数（适用于离散调度问题）
        int[] params = Arrays.stream(pos).mapToInt(x -> (int) Math.round(x)).toArray();
        return optFunction.calc(params);
    }

    /**
     * 评估第 i 个个体，并更新全局最优解和收敛曲线
     *
     * @param i 个体索引
     */
    private void evaluateIndividual(int i) {
        if (evaluations >= maxFEs) return;

        double fitness = evaluate(positions[i]);

        // 更新全局最优
        if (fitness < bestScore) {
            bestScore = fitness;
            System.arraycopy(positions[i], 0, bestPos, 0, dim);
        }

        // 记录收敛曲线（按评估次数）
        convergenceCurve[evaluations] = bestScore;
        evaluations++;
    }

    /**
     * 生成Lévy飞行步长（用于增强探索能力）
     * 公式参考原文 Eq. (9)
     *
     * @param d 维度
     * @return Lévy飞行步长向量
     */
    private double[] levyFlight(int d) {
        double beta = 1.5;
        // 计算Lévy分布的尺度参数σ
        double sigma = Math.pow(
                gamma(1 + beta) * Math.sin(Math.PI * beta / 2) /
                        (gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2)),
                1.0 / beta
        );

        double[] u = new double[d];
        double[] v = new double[d];
        for (int j = 0; j < d; j++) {
            u[j] = random.nextGaussian() * sigma;
            v[j] = random.nextGaussian();
        }

        double[] step = new double[d];
        for (int j = 0; j < d; j++) {
            step[j] = u[j] / Math.pow(Math.abs(v[j]), 1.0 / beta);
        }
        return step;
    }

    /**
     * Gamma函数近似计算（使用Lanczos方法）
     * 用于Lévy飞行中的参数计算
     *
     * @param x 输入值（x > 0）
     * @return Γ(x) 的近似值
     */
    private double gamma(double x) {
        if (x <= 0) return Double.NaN;
        return Math.exp(lanczosGammaLog(x));
    }

    /**
     * 使用Lanczos近似计算 ln(Γ(z))
     *
     * @param z 输入值（z > 0）
     * @return ln(Γ(z)) 的近似值
     */
    private double lanczosGammaLog(double z) {
        // Lanczos系数（g=7）
        double[] p = {
                0.99999999999980993,
                676.5203681218851,
                -1259.1392167224028,
                771.32342877765313,
                -176.61502916214059,
                12.507343278686905,
                -0.13857109526572012,
                9.9843695780195716e-6,
                1.5056327351493116e-7
        };

        if (z < 0.5) {
            // 利用反射公式 Γ(z)Γ(1−z) = π / sin(πz)
            return Math.log(Math.PI) - Math.log(Math.sin(Math.PI * z)) - lanczosGammaLog(1 - z);
        }

        z -= 1;
        double x = p[0];
        for (int i = 1; i < p.length; i++) {
            x += p[i] / (z + i);
        }
        double t = z + 7 + 0.5; // g = 7
        return 0.5 * Math.log(2 * Math.PI) + (z + 0.5) * Math.log(t) - t + Math.log(x);
    }

    /**
     * 边界处理：将越界的位置强制拉回 [lb, ub] 范围内
     */
    private void enforceBounds() {
        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                if (positions[i][j] < lb) positions[i][j] = lb;
                if (positions[i][j] > ub) positions[i][j] = ub;
            }
        }
    }

    /**
     * 执行PPO优化主流程
     *
     * @return 全局最优解（整数数组）
     */
    public int[] execute() {
        while (evaluations < maxFEs) {
            // 步骤1: 随机打乱个体顺序（Eq. 3）
            int[] indices = getRandomPermutation(population);
            double[][] Y = new double[population][dim];
            for (int i = 0; i < population; i++) {
                System.arraycopy(flockMemoryX[indices[i]], 0, Y[i], 0, dim);
            }

            // 步骤2: 计算适应度变换 F 和权重 E（Eq. 2）
            double maxF = Arrays.stream(flockMemoryF).max().orElse(0);
            double minF = Arrays.stream(flockMemoryF).min().orElse(0);
            double[] E = new double[population];
            double[] F = new double[population];
            for (int i = 0; i < population; i++) {
                F[i] = maxF + minF - flockMemoryF[i]; // 反向适应度
            }
            double maxFVal = Arrays.stream(F).max().orElse(1.0);
            for (int i = 0; i < population; i++) {
                E[i] = F[i] / (maxFVal + 1e-10); // 避免除零
            }

            // 步骤3: 计算群体平均距离 omega（Eq. 4）
            double totalDist = 0.0;
            for (int i = 0; i < population; i++) {
                double dist = 0.0;
                for (int j = 0; j < dim; j++) {
                    dist += Math.pow(positions[i][j] - Y[i][j], 2);
                }
                totalDist += Math.sqrt(dist) / dim;
            }
            double omega = totalDist / population;

            // 步骤4: “喷射逃脱”行为（Escape by ejecting, Eq. 5-6）
            double[] D = new double[population];
            for (int i = 0; i < population; i++) {
                for (int j = 0; j < dim; j++) {
                    double v = E[i] * Math.abs(positions[i][j] - Y[i][j]);
                    positions[i][j] = Y[i][j] + Math.cos(random.nextDouble() * Math.PI) * v;
                }
                // 计算个体与Y的距离 D(i)
                double dist = 0.0;
                for (int j = 0; j < dim; j++) {
                    dist += Math.pow(positions[i][j] - Y[i][j], 2);
                }
                D[i] = Math.sqrt(dist);
            }

            // 计算动态阈值 sigma
            double avgD = Arrays.stream(D).average().orElse(0.0);
            double sigma = avgD * ((1.0 - (double) evaluations / maxFEs) + 0.5);

            // 步骤5: 根据距离选择策略
            for (int i = 0; i < population; i++) {
                if (D[i] < sigma) {
                    // 性行为 + 性食同类（Sexual cannibalism, Eq. 7-8）
                    for (int j = 0; j < dim; j++) {
                        Y[i][j] = Y[i][j] + random.nextDouble() * E[i] * (positions[i][j] - Y[i][j]);
                    }
                    // 添加Lévy飞行扰动
                    double[] levy = levyFlight(dim);
                    double decay = Math.exp(1.0 - (double) evaluations / maxFEs);
                    for (int j = 0; j < dim; j++) {
                        positions[i][j] = Y[i][j] + levy[j] * omega * decay;
                    }
                } else {
                    // 捕食行为（Predation, Eq. 10）：向全局最优靠拢
                    for (int j = 0; j < dim; j++) {
                        positions[i][j] = bestPos[j] + Math.cos(random.nextDouble() * Math.PI) * (positions[i][j] - bestPos[j]);
                    }
                }
            }

            // 边界修正
            enforceBounds();

            // 评估新位置
            for (int i = 0; i < population && evaluations < maxFEs; i++) {
                evaluateIndividual(i);
            }

            // 更新个体记忆：保留历史最优
            for (int i = 0; i < population; i++) {
                double currentFit = evaluate(positions[i]);
                if (currentFit < flockMemoryF[i]) {
                    flockMemoryF[i] = currentFit;
                    System.arraycopy(positions[i], 0, flockMemoryX[i], 0, dim);
                }
            }
        }

        // 返回整数形式的最优解
        return Arrays.stream(bestPos)
                .mapToLong(Math::round)
                .mapToInt(Math::toIntExact) // 若 long 超出 int 范围会抛异常
                .toArray();
    }

    /**
     * 生成 0 到 n-1 的随机排列
     *
     * @param n 排列长度
     * @return 随机排列数组
     */
    private int[] getRandomPermutation(int n) {
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) arr[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = arr[i];
            arr[i] = arr[j];
            arr[j] = temp;
        }
        return arr;
    }

    // Getter 方法

    /**
     * 获取收敛曲线（仅包含已评估的部分）
     */
    public double[] getConvergenceCurve() {
        return Arrays.copyOf(convergenceCurve, evaluations);
    }

    /**
     * 获取全局最优解位置（实数）
     */
    public double[] getBestPos() {
        return bestPos;
    }

    /**
     * 获取全局最优适应度值
     */
    public double getBestScore() {
        return bestScore;
    }
}