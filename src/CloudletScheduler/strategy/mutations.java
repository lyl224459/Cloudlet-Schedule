package CloudletScheduler.strategy;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.TDistribution;

import java.util.Random;

/**
 * Copyright (C), 2024-11-28
 * FileName: mutations
 * Author:   LYL
 * Date:     2024/11/28 下午2:59
 * Description: 编译策略通用接口
 */
public interface mutations {
    Random random = new Random();

    static void adjustPositions(int dim, int agentIndex, double[][] positions, double lb, double ub) {
        // 遍历每个维度，调整位置
        for (int j = 0; j < dim; j++) {
            // 如果调整后的维度位置低于下界，则设置为下界
            if (positions[agentIndex][j] < lb) {
                positions[agentIndex][j] = lb;
            }
            // 如果调整后的维度位置高于上界，则设置为上界
            if (positions[agentIndex][j] > ub) {
                positions[agentIndex][j] = ub;
            }
        }
    }

    static void adjustPositions(int dim, double[] optimalPos, double lb, double ub) {
        for (int j = 0; j < dim; j++) {
            // 如果调整后的维度位置低于下界，则设置为下界
            if (optimalPos[j] < lb) {
                optimalPos[j] = lb;
            }
            // 如果调整后的维度位置高于上界，则设置为上界
            if (optimalPos[j] > ub) {
                optimalPos[j] = ub;
            }
        }
    }

    static void applyGaussianMutation(int dim, int agentIndex, double[][] positions, double mutationRate, double lb, double ub) {
        // 遍历代理的位置向量的每个维度
        for (int j = 0; j < dim; j++) {
            // 判断当前位置维度是否发生变异
            if (random.nextDouble() < mutationRate) {
                // 生成高斯噪声
                double gaussianNoise = random.nextGaussian();
                // 将高斯噪声添加到当前位置维度上，以实现变异
                positions[agentIndex][j] += gaussianNoise;
            }
        }
        adjustPositions(dim, agentIndex, positions, lb, ub);
    }

//    static void applyGaussianEliteMutation(int dim, int agentIndex, double[] optimalPos, double[][] positions, double mutationRate, double lb, double ub) {
//        for (int j = 0; j < dim; j++) {
//            // 如果随机数小于变异率，则对该维度进行变异
//            if (random.nextDouble() < mutationRate) {
//                double mu = optimalPos[j];
//                double sigma = (ub - lb) / 6.0;
//                NormalDistribution normalDist = new NormalDistribution(mu, sigma);
//                double r = normalDist.sample();
//                // 将高斯噪声添加到当前维度的位置上
//                positions[agentIndex][j] = positions[agentIndex][j] * r;
//            }
//        }
//        adjustPositions(dim, agentIndex, positions, lb, ub);
//    }
    static void applyGaussianEliteMutation(int dim, int agentIndex, double[] optimalPos, double[][] positions, double mutationRate, double lb, double ub) {
        for (int j = 0; j < dim; j++) {
            // 如果随机数小于变异率，则对该维度进行变异
            if (random.nextDouble() < mutationRate) {
                double mu = optimalPos[j];  // 精英位置的均值
                double sigma = (ub - lb) / 6.0;  // 标准差，确保大部分变异落在边界内
                // 创建一个高斯分布生成器
                NormalDistribution normalDist = new NormalDistribution(mu, sigma);
                // 从高斯分布中抽取一个样本作为噪声
                double r = normalDist.sample();
                // 将高斯噪声添加到当前维度的位置
                double mutatedValue = positions[agentIndex][j] + r;  // 使用加法方式
                // 保证变异后的值在边界范围内
                if (mutatedValue < lb) {
                    mutatedValue = lb;
                } else if (mutatedValue > ub) {
                    mutatedValue = ub;
                }
                // 更新当前个体的位置
                positions[agentIndex][j] = mutatedValue;
            }
        }
    // 调整所有位置，确保每个维度都在合理范围内
        adjustPositions(dim, agentIndex, positions, lb, ub);
    }
    /**
     * 生成柯西分布的随机数
     *
     * @return 柯西分布的随机数
     */
    static double generateCauchyRandom() {
        return Math.tan(Math.PI * (random.nextDouble() - 0.5));
    }

    static void applyCauchyMutation(int dim, int agentIndex, double[][] positions, double mutationRate, double lb, double ub) {
        for (int j = 0; j < dim; j++) {
            if (random.nextDouble() < mutationRate) {
                // 生成柯西分布的随机数
                double cauchyNoise = generateCauchyRandom();
                positions[agentIndex][j] += positions[agentIndex][j] * cauchyNoise;
            }
        }
        adjustPositions(dim, agentIndex, positions, lb, ub);
    }

    /**
     * 生成柯西分布的逆累计分布随机数
     *
     * @return 柯西分布的逆累计分布随机数
     */
    static double generateCauchyICDFRandom() {
        // 生成一个 [0, 1) 之间的均匀分布随机数
        double u = random.nextDouble();
        // 使用柯西分布的逆累积分布函数
        return Math.tan(Math.PI * (u - 0.5));
    }

    static void applyNCauchyMutation(int dim, double[] optimalPos, double mutationRate, double lb, double ub) {
        for (int j = 0; j < dim; j++) {
            if (random.nextDouble() < mutationRate) {
                // 生成柯西分布的逆累计分布随机数
                double cauchyNoise = generateCauchyICDFRandom();
                optimalPos[j] += cauchyNoise;
            }
        }
        adjustPositions(dim, optimalPos, lb, ub);
    }


    /**
     * 生成t分布的随机数
     *
     * @return t分布的随机数
     */
    static double generateTRandom(final int DEGREES_OF_FREEDOM) {
        TDistribution tDist = new TDistribution(DEGREES_OF_FREEDOM);
        return tDist.inverseCumulativeProbability(Math.random());
    }

    static void applyTMutation(int dim, double[] optimalPos, double mutationRate, double lb, double ub, int DEGREES_OF_FREEDOM) {
        for (int j = 0; j < dim; j++) {
            if (random.nextDouble() < mutationRate) {
                // 生成t分布的随机数
                double tNoise = generateTRandom(DEGREES_OF_FREEDOM);
                optimalPos[j] += tNoise;
            }
        }
        adjustPositions(dim, optimalPos, lb, ub);
    }

    static void applyDEBest1Mutation(int dim, double[][] positions, double[] optimalPos, double mutationRate, double lb, double ub, double F, int population) {
        for (int j = 0; j < dim; j++) {
            if (random.nextDouble() < mutationRate) {
                // 选择两个不同的随机个体
                int r1, r2;
                do {
                    r1 = random.nextInt(population);
                } while (r1 == 0); // 确保 r1 不是最佳个体
                do {
                    r2 = random.nextInt(population);
                } while (r2 == 0 || r2 == r1); // 确保 r2 不是最佳个体且不等于 r1

                // DE/best/1 变异
                double mutant = optimalPos[j] + F * (positions[r1][j] - positions[r2][j]);

                // 确保变异后的值仍在搜索空间内
                if (mutant < lb) {
                    mutant = lb;
                }
                if (mutant > ub) {
                    mutant = ub;
                }

                // 更新最优个体的位置
                optimalPos[j] = mutant;
            }
        }
    }

    static void randToBest(int dim, int agentIndex, double[][] positions, double[] optimalPos, int population, double lb, double ub) {
        Random rand = new Random();
        final double F = rand.nextDouble();
        for (int j = 0; j < dim; j++) {
            if (rand.nextDouble() < 0.5) {
                int r1 = random.nextInt(population); // 随机选择一个个体
                int r2;
                do {
                    r2 = random.nextInt(population); // 确保r2不等于r1
                } while (r2 == r1);

                double[] v1 = positions[r1];
                double[] v2 = positions[r2];

//                 positions[agentIndex] = new double[positions[agentIndex].length];

                for (int i = 0; i < positions[agentIndex].length; i++) {
                    positions[agentIndex][i] = positions[agentIndex][i] + F * (optimalPos[i] - positions[agentIndex][i]) + F * (v1[i] - v2[i]);
                }
            }
            adjustPositions(dim, agentIndex, positions, lb, ub);
        }
    }
}
