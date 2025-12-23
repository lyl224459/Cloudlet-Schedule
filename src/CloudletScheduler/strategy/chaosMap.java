package CloudletScheduler.strategy;

import java.util.Random;

/**
 * Copyright (C), 2024-11-28
 * FileName: chaosMap
 * Author:   LYL
 * Date:     2024/11/28 下午2:59
 * Description: 混沌函数通用接口
 */
public interface chaosMap {
    Random random = new Random();

    /**
     * 实现帐篷映射函数。
     * 帐篷映射是一种在动力系统和混沌理论研究中使用的数学函数。
     * 由于其图形类似于帐篷，因此得名“帐篷映射”。
     * 该函数是分段线性的，在不同的输入值范围内有不同的行为。
     *
     * @param x 输入值，表示要通过帐篷映射处理的值。
     *          输入值 x 应在 [0, 1] 范围内，表示一个比例或概率。
     * @return 帐篷映射变换的结果。
     * 如果 x 小于 0.5，返回值为 2 * x，表示在这个范围内的线性增加；
     * 否则，返回值为 2 * (1 - x)，表示在这个范围内的线性减少。
     */
    static double tentMap(double x) {
        if (x < 0.5) {
            return 2 * x;
        } else {
            return 2 * (1 - x);
        }
    }

    /**
     * 计算给定 x 的切比雪夫映射值。
     * 切比雪夫映射是混沌理论和非线性动力学领域中的一个数学概念。
     * 它是切比雪夫多项式的一个推广，定义使用余弦函数。
     * 该函数实现了固定参数 a = 4 的切比雪夫映射。
     *
     * @param x 输入值，应位于范围 [-1, 1] 内，表示某个角度的余弦值。
     * @return 计算得到的切比雪夫映射值，即角度乘以 a 后的余弦值。
     */
    static double chebyshevMap(double x) {
        // 定义切比雪夫映射的参数 a，值为 4
        int a = 4;
        // 使用余弦函数计算切比雪夫映射值
        return Math.cos(a * Math.acos(x));
    }

    /**
     * 使用圆形映射函数处理给定的输入值x
     * 该函数旨在通过一个特定的数学公式对输入值x进行变换，使其结果映射到一个圆形的周长上
     * 这在某些模拟或算法中很有用，以实现特定的数值稳定性或分布特性
     *
     * @param x 输入值，通常代表某种形式的输入变量或参数
     * @return 返回经过圆形映射函数处理后的结果
     */
    static double circleMap(double x) {
        // 定义2π的值，用于后续的计算
        double TWO_PI = 2 * Math.PI;
        // 定义参数A，控制映射的幅度
        double A = 0.5;
        // 定义参数B，作为偏移量
        double B = 0.2;
        // 计算A除以2π的结果，用于后续的计算
        double A_DIV_TWO_PI = A / TWO_PI;
        // 限制 x 的范围，减少数值稳定性问题
        x = x % 1;
        // 主要的圆形映射计算公式
        double result = x + B - A_DIV_TWO_PI * Math.sin(TWO_PI * x);
        // 确保模运算的结果是非负数
        if (result < 0) {
            result += 1;
        }
        // 返回经过映射后的结果，确保结果在[0,1)范围内
        return result % 1;
    }

    /**
     * 使用高斯映射
     * 该方法旨在将输入的x值映射到[0,1)区间内
     * 特别注意处理x为0和1的情况，以及x接近于0时的特殊情况
     *
     * @param x 输入值，代表需要映射的原始位置
     * @return 映射后的值，位于[0,1)区间内
     * @throws IllegalArgumentException 如果x过于接近0，抛出此异常以避免除零错误
     */
    static double gaussMouseMap(double x) {
        // 特殊处理 x 为 0 的情况
        if (x == 0) {
            return 0;
        }
        // 特殊处理 x 为 1 的情况
        if (x == 1) {
            return 0;
        }
        // 检查 x 是否接近于 0，避免除零错误
        if (Math.abs(x) < 1e-10) {
            throw new IllegalArgumentException("x is too close to zero");
        }
        // 计算 1/x
        double reciprocal = 1 / x;
        // 返回结果
        return reciprocal % 1.0;
    }

    /**
     * 根据给定的x值迭代映射生成下一个序列值
     * 该函数使用正弦函数来计算序列的下一个值，旨在模拟某种周期性或混沌行为
     *
     * @param x 输入的x值，作为正弦函数周期的决定因素，不应为零避免除以零的错误
     * @return 计算得到的下一个序列值
     */
    static double iterativeMap(double x) {
        // 定义常数a，作为正弦函数周期的调整参数
        double a = 0.7;
        // 计算并返回下一个序列值
        return Math.sin((a * Math.PI) / x);
    }

    static double logisticMap(double x) {
        int a = 4;
        return a * x * (1 - x);
    }

    /**
     * 将给定的值x映射到一个特定的百分比范围内
     * 此函数的目的是通过不同的线性变换，将输入值x映射到一个非线性的百分比输出
     * 它特别处理了四个不同的区间，每个区间都有自己的映射规则
     *
     * @param x 输入值，应位于[0, 1]区间内
     * @return 根据输入值x计算得到的百分比映射值
     */
    static double percentMap(double x) {
        double P = 0.4;
        // 当x等于P或1-P时，直接返回1，这是为了避免除以零的情况，并处理了函数的奇点
        if (x == P || x == 1 - P) {
            return 1; // 明确处理边界条件
        }
        // 以下四个条件分支分别处理了x所在的四个不同区间，每个区间都有自己的映射逻辑
        if (x >= 0 && x < P) {
            return x / P;
        } else if (x >= P && x < 0.5) {
            return (x - P) / (0.5 - P);
        } else if (x >= 0.5 && x <= 1 - P) {
            return (1 - P - x) / (0.5 - P);
        } else if (x > 1 - P && x <= 1) {
            return (1 - x) / P;
        }
        // 如果x不在任何上述定义的区间内，返回0
        return 0;
    }

    static double sineMap(double x) {
        return Math.sin(Math.PI * x);
    }

    static double singerlMap(double x) {
        double U = 1.07;
        return U * (7.86 * x - 23.31 * Math.sqrt(x) + 28.75 * Math.pow(x, 3) - 13.302875 * Math.pow(x, 4));
    }

    static double sinusoidalMap(double x) {
        return (2.3 * Math.pow(x, 2) * Math.sin(Math.PI * x));
    }

    static double fuchMap(double x) {
        return Math.cos(Math.pow(Math.sqrt(x), -1));
    }

    /**
     * 根据特定的数学模型映射输入值x到[0,1)范围内的值
     * 该模型通过一系列复杂的数学变换，旨在提供一种非线性的映射方式
     * 主要用于需要对输入值进行复杂调整以适应特定输出范围的场景
     *
     * @param x 输入值，应位于[0,1)范围内
     * @return 根据数学模型计算得到的映射值
     */
    static double SPMMap(double x) {
        // 定义效率因子ETA，用于调整映射的非线性程度
        final double ETA = 0.4;
        // 定义波动强度U，用于引入正弦波动
        final double U = 0.3;
        // 初始化返回值val
        double val = 0.0;

        // 当x位于[0, ETA)区间时，应用第一种映射方式
        if (x >= 0 && x < ETA) {
            val = x / ETA + U * Math.sin(Math.PI * x) + random.nextDouble();
            return val % 1;
            // 当x位于[ETA, 0.5)区间时，应用第二种映射方式
        } else if (x >= ETA && x < 0.5) {
            val = (x - ETA) / (0.5 - ETA) + U * Math.sin(Math.PI * x) + random.nextDouble();
            return val % 1;
            // 当x位于[0.5, 1 - ETA]区间时，应用第三种映射方式
        } else if (x >= 0.5 && x <= 1 - ETA) {
            val = ((1 - x) / ETA) / (0.5 - ETA) + U * Math.sin(Math.PI * (1 - x)) + random.nextDouble();
            return val % 1;
            // 当x位于(1 - ETA, 1)区间时，应用第四种映射方式
        } else if (x > 1 - ETA && x < 1) {
            val = (1 - x) / ETA + U * Math.sin(Math.PI * (1 - x)) + random.nextDouble();
            return val % 1;
        }
        // 对于不在上述区间的x值，返回0
        return 0;
    }

    static double ICMICmap(double x) {
        final int a = 4;
        return Math.sin(a / x);
    }

    /**
     * 实现一个帐篷逻辑余弦映射函数。
     * 该函数根据输入 x 和随机数 R 计算特定数学表达式的值。
     * 目的是为给定输入生成具有特定特性的随机输出。
     *
     * @param x 输入值，应在 [0, 1] 范围内。
     * @return 计算得到的余弦映射值。
     */
    static double tentLogisticCosineMap(double x) {
        // 生成一个在 [0, 1) 范围内的随机数 R，用于后续计算。
        final double R = random.nextDouble();
        if (x < 0.5) {
            // 当 x 小于 0.5 时，使用特定公式计算并返回结果。
            return Math.cos(Math.PI * (2 * R * x + 4 * (1 - R) * x * (1 - x) - 0.5));
        } else {
            // 当 x 不小于 0.5 时，使用另一个公式计算并返回结果。
            return Math.cos(Math.PI * (2 * R * (1 - x) + 4 * (1 - R) * x * (1 - x) - 0.5));
        }
    }

    /**
     * 根据给定的输入x，计算并返回一个基于正弦和余弦函数的映射值
     * 该方法旨在通过引入随机性来模拟自然界的复杂动态行为
     *
     * @param x 输入值，应位于[0, 1]区间内，代表需要进行映射的变量
     * @return 返回一个经过非线性映射后的值，用于进一步的动态系统模拟
     */
    static double sineTentCosineMap(double x) {
        // 生成一个随机数，用于后续计算中的随机性引入
        final double R = random.nextDouble();
        // 根据输入x的值，选择不同的计算分支
        if (x < 0.5) {
            // 当x小于0.5时，使用特定的公式计算并返回映射值
            return Math.cos(Math.PI * (R * Math.sin(Math.PI * x)) + 2 * (1 - R) * x - 0.5);
        } else {
            // 当x大于等于0.5时，使用另一种公式计算并返回映射值
            return Math.cos(Math.PI * (R * Math.sin(Math.PI * x)) + 2 * (1 - R) * (1 - x) - 0.5);
        }
    }

    /**
     * 实现一个逻辑正弦余弦映射函数。
     * 该函数结合了逻辑映射和三角函数（正弦和余弦）的概念，创建了一个更复杂和动态的行为。
     * 常用于计算智能和混沌理论领域，模拟复杂系统的行为。
     *
     * @param x 输入值，应在范围 [0,1] 内，表示系统的初始条件或状态。
     * @return 应用逻辑正弦余弦映射后的值，也在范围 [0,1] 内。
     */
    static double logisticSineCosineMap(double x) {
        // 生成一个在范围 [0,1] 内的随机数 R，表示映射函数中的随机参数。
        final double R = random.nextDouble();

        // 计算逻辑正弦余弦映射值。
        // 这一行代码将逻辑映射函数 (4 * R * x * (1 - x)) 与正弦和余弦三角函数结合起来，
        // 创建了一个复杂的非线性映射关系。
        // 目的是通过简单的数学表达式模拟复杂系统的动态行为。
        return Math.cos(Math.PI * (4 * R * x * (1 - x) + (1 - R) * Math.sin(Math.PI * x) - 0.5));
    }


    //TODO 暂时没想到好的实现方法
    static double henonMap(double x, double y) {
//        final double a = 1.4;
//        final double b = 0.3;
//        if(0.3 != y){
//
//        }
        return 0;
    }

    /**
     * 实现一个改进的Logistic帐篷映射（Logistic Tent Map）函数
     * 该函数根据输入的x值，通过一系列数学变换，返回一个在[0,1)范围内的值
     * 这些变换基于Logistic映射和帐篷映射的原理，使用一个常量R来控制映射的特性
     *
     * @param x 输入值，应位于[0,1]区间内，代表映射函数的自变量
     * @return 返回经过改进的Logistic帐篷映射后的值，范围在[0,1)内
     */
    static double logisticTentMap(double x) {
        // 定义控制参数R，其值决定了映射函数的行为
        final double R = 0.3;
        // 初始化变量val用于存储计算结果
        double val = 0.0;

        // 根据x的值选择不同的计算公式
        if (x < 0.5) {
            // 当x小于0.5时，应用一种映射规则
            val = R * x * (1 - x) + (4 - R) * x / 2;
            // 返回计算结果的模1值，确保结果在[0,1)范围内
            return val % 1;
        } else {
            // 当x大于等于0.5时，应用另一种映射规则
            val = R * (1 - x) * x + (4 - R) * (1 - x) / 2;
            // 同样返回计算结果的模1值，确保结果在[0,1)范围内
            return val % 1;
        }
    }

    /**
     * 计算给定数字的三次映射值
     * 该方法实现了一个特定的数学模型，即三次映射，常用于数学建模和算法仿真
     * 三次映射公式定义为：A*x*(1-sqrt(x))，其中 A 是预定义的常量
     *
     * @param x 输入值，应为一个合理的双精度浮点数以确保计算结果正确
     * @return 返回 x 的三次映射值，结果为双精度浮点数
     */
    static double cubicMap(double x) {
        // 定义常量 A，用于三次映射公式
        final double A = 2.595;
        // 应用三次映射公式计算并返回结果
        return A * x * (1 - Math.sqrt(x));
    }


    /**
     * 实现Bernoulli映射的函数
     * 该映射是一种数学函数，常用于混沌理论和随机数生成
     * 它根据输入的值x和一个常数R，决定输出值
     *
     * @param x 输入值，应位于[0, 1]区间内
     * @return 根据Bernoulli映射计算得到的输出值
     */
    static double bernoulliMap(double x) {
        // 定义常数R，表示Bernoulli映射中的一个关键参数
        final double R = 0.5;

        // 当x位于[0, 1-R]区间时，应用映射的第一部分
        if (x >= 0 && x <= (1 - R)) {
            return x / (1 - R);
        } else {
            // 当x位于(1-R, 1]区间时，应用映射的第二部分
            return (x - 1 + R) / R;
        }
    }

    /**
     * 根据输入的值x，通过Kenk映射函数计算并返回映射后的值.
     * Kenk映射是一种分段线性函数，常用于特定的数学建模或算法中.
     *
     * @param x 输入到Kenk映射函数中的值，应为一个双精度浮点数.
     * @return 返回通过Kenk映射函数计算后的双精度浮点数值.
     */
    static double kenkMap(double x) {
        // 定义常量R，用于Kenk映射函数中的特定阈值.
        final double R = 0.4;

        // 当输入值x在[0, R]区间内时，返回x除以R的结果.
        if (x >= 0 && x <= R) {
            return x / R;
        } else {
            // 当输入值x不在[0, R]区间内时，返回(1-x)除以(1-R)的结果.
            return (1 - x) / (1 - R);
        }
    }
}
