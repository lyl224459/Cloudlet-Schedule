# MO-IPPO算法说明文档

## 概述

**MO-IPPO (Multi-Objective Improved Predatory Prey Optimization)** 是基于MO-PPO算法的改进版本，专门针对云计算任务调度的4个多目标函数进行优化。

## 技术实现

### 使用的第三方库

MO-IPPO 充分利用了 **Apache Commons Math 3.6.1** 库的功能：

- **`org.apache.commons.math3.special.Gamma`**：用于计算Gamma函数，用于Levy飞行的sigma计算
- **`org.apache.commons.math3.distribution.NormalDistribution`**：用于生成正态分布随机数，替代Java原生的`random.nextGaussian()`

这些库函数提供了更高的数值精度和更好的性能。

## 算法改进点

### 1. 改进的初始化策略

**问题**：原始MO-PPO使用连续值随机初始化，然后四舍五入到整数，导致很多不同的连续值映射到相同的整数解，减少了初始种群的多样性。

**改进**：
- **整数随机初始化**：直接生成整数解，避免连续值映射问题
- **贪心初始化**：10%的个体使用贪心策略初始化，提高初始解质量
- **更好的多样性**：确保初始种群在解空间中有更好的分布

```java
// 整数随机初始化
for (int j = 0; j < dim; j++) {
    positions[i][j] = (int)(lb + random.nextInt((int)(ub - lb + 1)));
}
```

**使用commons-math3的优势**：
- 使用`NormalDistribution.sample()`替代`random.nextGaussian()`，提供更好的数值稳定性
- 使用`Gamma.gamma()`函数，避免自己实现复杂的Gamma函数计算

### 2. 目标函数感知的搜索策略

**针对4个目标函数的特点**：
- **Makespan**：最大完成时间，需要平衡VM负载
- **CostEfficiency**：成本效率比，需要选择性价比高的VM
- **LoadBalanceIndex**：负载均衡指数，需要均匀分配任务
- **ResourceWaste**：资源浪费率，需要提高资源利用率

**改进**：
- 根据目标函数的权衡关系调整搜索行为
- 自适应探索/利用平衡，根据搜索进度动态调整

### 3. 多样性增强机制

**改进**：
- 改进的拥挤距离计算
- 更好的多样性维护策略
- 自适应存档管理

### 4. 自适应参数调整

**自适应Levy飞行参数**：
```java
adaptiveBeta = 1.5 - 0.4 * progress; // 随搜索进度从探索到利用

// 使用commons-math3的Gamma函数计算Levy飞行的sigma
double sigma = Math.pow(
    Gamma.gamma(1 + beta) * Math.sin(Math.PI * beta / 2) /
    (Gamma.gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2)),
    1.0 / beta
);

// 使用commons-math3的NormalDistribution生成随机数
double u = normal.sample() * sigma;
double v = Math.abs(normal.sample());
```

**自适应探索率**：
```java
explorationRate = 0.5 * (1.0 - progress); // 随搜索进度减小
```

### 5. 局部搜索增强

**针对优秀解的局部优化**：
- 每10代对优秀解进行任务重分配优化
- 随机选择部分任务进行VM重分配
- 如果新解不被当前解支配，则接受

```java
// 局部搜索：针对优秀解进行任务重分配优化
private void localSearch(int idx) {
    // 随机选择一些任务进行重分配
    // 如果新解不被当前解支配，则接受
}
```

### 6. 停滞检测和重启机制

**问题**：算法可能陷入局部最优，导致Pareto前沿不再改进。

**改进**：
- 监控存档大小变化，检测停滞
- 当停滞超过阈值时，重启部分个体
- 保持搜索的持续性和多样性

```java
if (stagnationCount > STAGNATION_THRESHOLD) {
    // 重启部分个体
    int restartCount = population / 5;
    // ...
}
```

## 算法流程

1. **初始化阶段**：
   - 10%个体使用贪心初始化
   - 90%个体使用整数随机初始化
   - 评估所有个体并更新Pareto存档
   - 保存第一代Pareto存档快照

2. **主循环**（直到达到最大函数评估次数）：
   - 更新自适应参数（Levy β、探索率等）
   - 计算伪适应度（基于到Pareto前沿的距离）
   - 根据距离阈值决定探索或利用行为
   - 更新位置并评估
   - 更新个体记忆（如果新解不被记忆中的解支配）
   - 每10代对优秀解进行局部搜索
   - 检测停滞并执行重启机制

3. **返回最终Pareto存档**

## 使用方法

### 1. 在代码中使用

MO-IPPO已经集成到`SchedulerFactory`中，可以直接在实验中使用：

```java
// 在SchedulerFactory.java中已注册
new SchedulerFactory("MOIPPO", MOIPPOScheduler::new)
```

### 2. 运行实验

运行`MainRunner.java`，MO-IPPO会自动包含在实验列表中，结果会保存到：
- `results/run_X/MOIPPO.csv`：单目标性能指标
- `results/run_X/pareto_fronts/MOIPPO_trial_X_final.csv`：最后一代Pareto前沿
- `results/run_X/pareto_fronts/MOIPPO_trial_X_first.csv`：第一代Pareto前沿

### 3. 参数配置

算法参数在`MainRunner.Config`中配置：
- `POPULATION = 50`：种群大小
- `MAX_ITER = 1000`：最大迭代次数
- `ARCHIVE_SIZE = 200`：Pareto存档大小

## 与MO-PPO的对比

| 特性 | MO-PPO | MO-IPPO |
|------|--------|---------|
| 初始化 | 连续值随机初始化 | 整数随机初始化 + 贪心初始化 |
| 参数调整 | 固定参数 | 自适应参数调整 |
| 局部搜索 | 无 | 针对优秀解的局部搜索 |
| 停滞处理 | 无 | 停滞检测和重启机制 |
| 多样性维护 | 基础拥挤距离 | 改进的多样性维护 |

## 预期改进效果

1. **更好的初始解质量**：贪心初始化提供更好的起点
2. **更高的解多样性**：整数初始化避免映射问题
3. **更好的收敛性**：自适应参数和局部搜索提高收敛速度
4. **更强的鲁棒性**：停滞检测和重启机制避免早熟收敛

## 文件结构

```
src/CloudletScheduler/MOOptimizer/moippo/
├── MOImprovedPPO.java      # 核心算法实现
└── MOIPPOScheduler.java    # 调度器封装类
```

## 注意事项

1. **整数优化**：MO-IPPO专门针对离散优化问题（VM分配）设计，使用整数编码
2. **4目标函数**：算法针对4个目标函数（Makespan, CostEfficiency, LoadBalanceIndex, ResourceWaste）优化
3. **计算复杂度**：局部搜索和停滞检测会增加一定的计算开销，但通常可以忽略不计

## 未来改进方向

1. **更智能的贪心初始化**：基于VM负载和任务特征的贪心策略
2. **多策略协同**：结合多种搜索策略
3. **目标分解**：使用目标分解方法处理多目标优化
4. **并行化**：利用多线程加速计算

## 参考文献

- 基于MO-PPO算法的改进
- 针对云计算任务调度多目标优化的特殊设计
