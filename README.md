# Cloudlet-Scheduler

[![CloudSim](https://img.shields.io/badge/CloudSim-5.0-blue.svg)](https://github.com/Cloudslab/cloudsim)
[![Java](https://img.shields.io/badge/Java-8+-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/license-GPL--3.0-green.svg)](LICENSE)

> 基于 CloudSim 5.0 的云任务调度算法仿真平台，实现了 15+ 种单目标和多目标优化算法，用于评估云计算环境下的任务调度性能。

## 📋 项目简介

本项目是一个云计算任务调度仿真平台，基于 CloudSim 5.0 框架，实现了多种元启发式优化算法和传统调度算法，用于解决云环境下的任务调度问题。项目支持单目标优化和多目标优化两种场景，可评估不同调度策略在 **完成时间（Makespan）**、**成本（Cost）**、**负载均衡（Load Balance）** 和 **资源利用率（Resource Utilization）** 等4个维度的性能表现。

**重要特性**：
- 🔹 **单目标实验**：使用 `DatacenterCreator.createCloudlets1` 生成均匀分布的任务
- 🔹 **多目标实验**：使用 `DatacenterCreator.createCloudlets` 生成对数正态分布的任务（更接近真实场景）

### 核心特性

- ✅ **15+ 种调度算法**：涵盖传统算法、群智能算法、混合算法和多目标优化算法
- ✅ **多目标优化支持**：基于 Pareto 前沿的多目标优化框架
- ✅ **灵活的实验框架**：支持批量运行、多次试验、自动统计分析
- ✅ **异构虚拟机环境**：模拟低、中、高三种性能等级的虚拟机
- ✅ **详细的性能指标**：包括 Makespan、Cost、Load Balance、Resource Utilization、TotalTime 等
- ✅ **可视化支持**：提供 Python 绘图脚本用于结果分析

## 🏗️ 项目结构

```
Cloudlet-Scheduler-mydev/
├── src/CloudletScheduler/
│   ├── Optimizer/              # 单目标优化算法
│   │   ├── woa/               # 鲸鱼优化算法 (WOA)
│   │   ├── hwga/              # 混合鲸鱼遗传算法 (HWGA)
│   │   ├── ga/                # 遗传算法 (GA)
│   │   ├── pso/               # 粒子群优化 (PSO)
│   │   ├── gwo/               # 灰狼优化 (GWO)
│   │   ├── hho/               # 哈里斯鹰优化 (HHO)
│   │   ├── dbo/               # 蜣螂优化 (DBO)
│   │   ├── ppo/               # 捕食者-猎物优化 (PPO)
│   │   ├── iwoa/              # 改进鲸鱼优化 (IWOA)
│   │   ├── sfoa/              # 向日葵优化 (SFOA)
│   │   ├── sequoia/           # 红杉优化 (Sequoia)
│   │   ├── cco/               # 珊瑚礁优化 (CCO)
│   │   ├── hso/               # 混合群优化 (HSO)
│   │   ├── minmin/            # Min-Min 算法
│   │   └── random/            # 随机调度算法
│   ├── MOOptimizer/           # 多目标优化算法
│   │   ├── mowoa/             # 多目标鲸鱼优化 (MO-WOA)
│   │   ├── mogwo/             # 多目标灰狼优化 (MO-GWO)
│   │   ├── mohho/             # 多目标哈里斯鹰优化 (MO-HHO)
│   │   ├── modbo/             # 多目标蜣螂优化 (MO-DBO)
│   │   ├── moppo/             # 多目标捕食者优化 (MO-PPO)
│   │   ├── moppo2/            # 增强型多目标捕食者优化
│   │   ├── moiwoa/            # 多目标改进鲸鱼优化 (MO-IWOA)
│   │   ├── mosfoa/            # 多目标向日葵优化 (MO-SFOA)
│   │   ├── mosequoia/         # 多目标红杉优化 (MO-Sequoia)
│   │   └── ParetoArchive.java # Pareto 前沿存档管理
│   ├── datacenter/            # 数据中心模型与评估函数
│   │   ├── Constants.java     # 仿真常量配置
│   │   ├── Scheduler.java     # 调度器接口
│   │   ├── OptFunction.java   # 单目标优化函数
│   │   ├── OptFunctionMulti.java  # 多目标优化函数
│   │   ├── MultiObjectiveEvaluator.java  # 多目标评估器
│   │   └── ObjectiveValues.java  # 目标值封装类
│   ├── runs/                  # 仿真运行与结果分析
│   │   ├── MainRunner.java    # 批量实验运行器
│   │   ├── SimulationRunner.java  # 单次仿真运行器
│   │   ├── SchedulerFactory.java  # 调度器工厂
│   │   ├── ResultAnalyzer.java    # 结果分析器
│   │   ├── SimulationResult.java  # 仿真结果封装
│   │   └── DatacenterCreator.java # 数据中心创建器
│   ├── strategy/              # 优化策略
│   │   ├── chaosMap.java      # 混沌映射策略
│   │   └── mutations.java     # 变异策略
│   ├── Main.java              # 单次仿真入口
│   └── mmain.java             # 备用主入口
├── draw/                      # 可视化绘图脚本和文档
│   ├── drawpicture.py         # Python 结果绘图脚本
│   ├── doaw.ipynb             # Jupyter 绘图笔记本
│   ├── result_visualization.ipynb  # 结果可视化笔记本
│   ├── pareto_front_visualization.ipynb  # Pareto前沿可视化
│   ├── multi_algorithm_comparison.ipynb   # 多算法对比分析
│   ├── moppo_improvements_analysis.ipynb # MO-PPO改进分析
│   ├── BatchRunner使用说明.md           # BatchRunner使用文档
│   ├── MO-PPO改进算法分析说明.md        # MO-PPO算法说明
│   ├── 经典多目标算法说明.md            # NSGA-II、MOEA/D、SPEA2说明
│   └── Pareto前沿可视化使用说明.md      # Pareto前沿可视化指南
├── libs/                      # 第三方库（JAR文件）
│   ├── cloudsim5.0.jar        # CloudSim 5.0 库
│   └── commons-math3-3.6.1.jar # Apache Commons Math 库
└── results/                   # 实验结果存储目录
    └── run_X/                 # 第X次实验的结果目录
```

## 🧬 实现的调度算法

### 单目标优化算法

| 算法缩写 | 算法全称 | 类型 | 说明 |
|---------|---------|------|------|
| **WOA** | Whale Optimization Algorithm | 群智能 | 鲸鱼优化算法 |
| **HWGA** | Hybrid Whale Genetic Algorithm | 混合算法 | 混合鲸鱼遗传算法 |
| **GA** | Genetic Algorithm | 进化算法 | 遗传算法 |
| **PSO** | Particle Swarm Optimization | 群智能 | 粒子群优化 |
| **GWO** | Grey Wolf Optimizer | 群智能 | 灰狼优化算法 |
| **HHO** | Harris Hawks Optimization | 群智能 | 哈里斯鹰优化 |
| **DBO** | Dung Beetle Optimizer | 群智能 | 蜣螂优化算法 |
| **PPO** | Predatory-Prey Optimization | 群智能 | 捕食者-猎物优化 |
| **IWOA** | Improved Whale Optimization | 改进算法 | 改进鲸鱼优化 |
| **SFOA** | Sunflower Optimization | 群智能 | 向日葵优化算法 |
| **Sequoia** | Sequoia Optimization | 群智能 | 红杉优化算法 |
| **CCO** | Coral Reef Optimization | 群智能 | 珊瑚礁优化 |
| **HSO** | Hybrid Swarm Optimization | 混合算法 | 混合群优化 |
| **Min-Min** | Min-Min Algorithm | 传统算法 | 最小-最小算法 |
| **Random** | Random Scheduler | 基准算法 | 随机调度 |

### 多目标优化算法

所有单目标算法均有对应的多目标版本（MO-前缀），采用 **Pareto 支配** 和 **外部存档** 机制实现多目标优化。

| 算法缩写 | 算法全称 | 类型 | 说明 |
|---------|---------|------|------|
| **MO-WOA** | Multi-Objective Whale Optimization | 群智能 | 多目标鲸鱼优化 |
| **MO-GWO** | Multi-Objective Grey Wolf Optimizer | 群智能 | 多目标灰狼优化 |
| **MO-HHO** | Multi-Objective Harris Hawks Optimization | 群智能 | 多目标哈里斯鹰优化 |
| **MO-DBO** | Multi-Objective Dung Beetle Optimizer | 群智能 | 多目标蜣螂优化 |
| **MO-PPO** | Multi-Objective Predatory-Prey Optimization | 群智能 | 多目标捕食者优化 |
| **MO-PPO2** | Enhanced Multi-Objective PPO | 改进算法 | 增强型多目标捕食者优化 |
| **MO-PPO2E** | Enhanced Multi-Objective PPO v2 | 改进算法 | 增强型多目标捕食者优化 v2 |
| **MO-IPPO** | Multi-Objective Improved PPO | 改进算法 | 改进的多目标捕食者优化（见下方详细说明） |
| **MO-IWOA** | Multi-Objective Improved WOA | 改进算法 | 多目标改进鲸鱼优化 |
| **MO-SFOA** | Multi-Objective Sunflower Optimization | 群智能 | 多目标向日葵优化 |
| **MO-Sequoia** | Multi-Objective Sequoia Optimization | 群智能 | 多目标红杉优化 |
| **NSGA-II** | Non-dominated Sorting Genetic Algorithm II | 经典算法 | 非支配排序遗传算法 II |
| **MOEA/D** | Multi-Objective Evolutionary Algorithm based on Decomposition | 经典算法 | 基于分解的多目标进化算法 |
| **SPEA2** | Strength Pareto Evolutionary Algorithm 2 | 经典算法 | 强度 Pareto 进化算法 2 |

#### MO-IPPO 算法说明

**MO-IPPO (Multi-Objective Improved Predatory Prey Optimization)** 是基于 MO-PPO 的改进版本，专门针对云计算任务调度的 4 个多目标函数进行优化。

**主要改进**：
1. **改进初始化策略**：整数随机初始化 + 贪心初始化（10% 个体）
2. **目标函数感知搜索**：根据 4 个目标函数特点自适应调整搜索行为
3. **多样性增强机制**：改进的拥挤距离计算和多样性维护
4. **自适应参数调整**：根据搜索进度动态调整 Levy 飞行参数
5. **局部搜索增强**：针对优秀解进行局部优化

**技术特点**：
- 使用 Apache Commons Math 3.6.1 库进行高精度数学计算

## 🚀 快速开始

### 环境要求

- **Java**: JDK 11 或更高版本
- **Maven**: 3.6+（用于项目构建和依赖管理）
- **CloudSim**: 5.0（通过 Maven 自动下载）
- **Apache Commons Math**: 3.6.1（用于数学计算，如 Gamma 函数、正态分布等）
- **Python**: 3.7+（可选，用于结果可视化）

### 项目构建

```bash
# 克隆项目
git clone <repository-url>
cd Cloudlet-Scheduler-mydev

# 使用 Maven 编译项目
mvn clean compile

# 编译后的 class 文件位于 out/compiled/ 目录
```

**依赖说明**：
- CloudSim 5.0：从 JitPack 自动下载
- Apache Commons Math 3.6.1：用于 MO-IPPO 等算法的高级数学计算

### 运行单次仿真

项目提供了两个单次仿真入口：

#### Main.java（推荐）

`src/CloudletScheduler/Main.java` 是主要的单次仿真入口，适合快速测试单个算法：

```java
// 编辑 src/CloudletScheduler/Main.java
public class Main {
    private static final int CLOUDLET_N = 1000; // 设置任务数量
    
    public static void main(String[] args) throws Exception {
        // 创建数据中心、虚拟机和任务
        // ...
        
        // 选择调度算法（可选：WOAScheduler, DBOScheduler 等）
        Scheduler scheduler = new WOAScheduler(cloudletList, vmList);
        scheduler.schedule();
        
        // 启动仿真
        CloudSim.startSimulation();
        
        // 打印结果
        printCloudletList(broker.getCloudletReceivedList());
    }
}
```

运行方式：
```bash
java -cp "out/compiled:libs/*" CloudletScheduler.Main
```

#### mmain.java（多调度器对比）

`src/CloudletScheduler/mmain.java` 用于对比多个调度器，每个算法的结果保存到独立的 CSV 文件：

```java
// 配置要对比的调度器列表
public static final List<SchedulerFactory> SCHEDULERS = Arrays.asList(
    new SchedulerFactory("WOA", WOAScheduler::new),
    new SchedulerFactory("DBO", DBOScheduler::new),
    new SchedulerFactory("Random", RandomScheduler::new)
);
```

运行方式：
```bash
java -cp "out/compiled:libs/*" CloudletScheduler.mmain
```

### 运行批量实验

#### 1. 配置实验参数

编辑 `src/CloudletScheduler/runs/MainRunner.java`：

```java
public class MainRunner {
    public static class Config {
        public static int CLOUDLET_N = 300;               // 云任务数量
        public static final int NUM_USER = 1;                    // 用户数量
        public static final int TRIALS_PER_EXPERIMENT = 10;       // 每个算法运行次数
        public static final int POPULATION = 50;                  // 种群大小
        public static final int MAX_ITER = 1000;                 // 最大迭代次数
        public static final int ARCHIVE_SIZE = 200;              // Pareto存档大小（多目标算法）
    }
}
```

#### 2. 选择任务生成器（重要！）

**根据实验类型选择对应的任务生成器**：

编辑 `src/CloudletScheduler/runs/SimulationRunner.java`：

**单目标实验配置**：
```java
// 第33行：将 createCloudlets 改为 createCloudlets1
List<Cloudlet> cloudletList = DatacenterCreator.createCloudlets1(brokerId, rand);
```

**多目标实验配置**：
```java
// 第33行：使用 createCloudlets（默认）
List<Cloudlet> cloudletList = DatacenterCreator.createCloudlets(brokerId, rand);
```

#### 3. 选择要运行的算法

编辑 `src/CloudletScheduler/runs/SchedulerFactory.java`：

**单目标算法示例**（需要取消注释）：
```java
public static final List<SchedulerFactory> ALL = Arrays.asList(
    new SchedulerFactory("WOA", WOAScheduler::new),
    new SchedulerFactory("IWOA", IWOAScheduler::new),
    new SchedulerFactory("PSO", PSOScheduler::new),
    new SchedulerFactory("DBO", DBOScheduler::new),
    new SchedulerFactory("HHO", HHOScheduler::new),
    new SchedulerFactory("GWO", GWOScheduler::new),
    new SchedulerFactory("PPO", PPOScheduler::new),
    new SchedulerFactory("SFOA", SFOAScheduler::new),
    new SchedulerFactory("sequoia", SequoiaScheduler::new),
    new SchedulerFactory("HSO", HSOScheduler::new)
);
```

**多目标算法示例**（当前默认配置）：
```java
public static final List<SchedulerFactory> ALL = Arrays.asList(
    new SchedulerFactory("MOPPO", MOPPOScheduler::new),
    new SchedulerFactory("MOPPO2", MOPPO2Scheduler::new),
    new SchedulerFactory("MOWOA", MOWOAScheduler::new),
    new SchedulerFactory("MODBO", MODBOScheduler::new),
    new SchedulerFactory("MOHHO", MOHHOScheduler::new),
    new SchedulerFactory("MOGWO", MOGWOScheduler::new),
    new SchedulerFactory("NSGAII", NSGAIIScheduler::new),
    new SchedulerFactory("MOEAD", MOEADScheduler::new),
    new SchedulerFactory("SPEA2", SPEA2Scheduler::new)
);
```

#### 4. 运行实验

```bash
# 编译项目
mvn compile

# 运行批量实验
java -cp "target/classes:libs/*" CloudletScheduler.runs.MainRunner
```

运行后，结果将保存在 `results/run_X/` 目录下：
- `{AlgorithmName}.csv`: 各算法的详细试验数据（包含 Makespan、TotalTime、LoadBalance、Cost）
- `summary_avg.csv`: 所有算法的平均性能对比
- `pareto_fronts/`: 多目标算法的 Pareto 前沿数据（仅多目标实验）
  - `{AlgorithmName}_trial_{N}_first.csv`: 第一代 Pareto 前沿
  - `{AlgorithmName}_trial_{N}_final.csv`: 最终 Pareto 前沿

#### 5. 批量运行不同任务数量的实验（可选）

使用 `BatchRunner.java` 可以自动运行多个不同任务数量的实验：

```bash
# 运行批量实验（任务数：100, 200, 300, ..., 1000）
java -cp "out/compiled:libs/*" CloudletScheduler.runs.BatchRunner
```

`BatchRunner` 会自动：
- 依次设置任务数量为 100, 200, 300, ..., 1000
- 为每个任务数量运行一次完整的实验
- 在每次实验目录中保存 `experiment_info.txt` 记录实验配置
- 自动递增实验编号

**注意**：使用 `BatchRunner` 前，同样需要配置 `SimulationRunner.java` 中的任务生成器和 `SchedulerFactory.java` 中的算法列表。

### 配置虚拟机环境

编辑 `src/CloudletScheduler/datacenter/Constants.java`：

```java
public interface Constants {
    // 虚拟机性能配置（MIPS）
    int L_MIPS = 1000;   // 低性能 VM
    int M_MIPS = 2000;   // 中性能 VM
    int H_MIPS = 4000;   // 高性能 VM
    
    // 虚拟机价格（$/秒）
    double L_PRICE = 0.1;   // 低性能 VM 价格
    double M_PRICE = 0.5;   // 中性能 VM 价格
    double H_PRICE = 1.0;    // 高性能 VM 价格
    
    // 虚拟机数量
    int L_VM_N = 4;  // 低性能 VM 数量
    int M_VM_N = 3;  // 中性能 VM 数量
    int H_VM_N = 2;  // 高性能 VM 数量
    
    // 虚拟机资源配置
    int RAM = 2048;              // 每个 VM 的内存（MB）
    long STORAGE = 100000;       // VM 存储容量（MB）
    long IMAGE_SIZE = 10000;      // VM 镜像大小（MB）
    int BW = 1024;                // VM 带宽（Mbps）
}
```

## 🔬 单目标 vs 多目标实验配置

### 实验类型说明

本项目支持两种类型的实验：

1. **单目标优化实验**：优化单一目标（通常是 Makespan），其他指标作为约束或参考
2. **多目标优化实验**：同时优化多个目标（Makespan、Cost、Load Balance、Resource Utilization），寻找 Pareto 最优解集

### 任务生成器选择指南

| 实验类型 | 任务生成器 | 任务分布特征 | 适用算法 |
|---------|-----------|-------------|---------|
| **单目标实验** | `createCloudlets1` | 均匀随机分布 | WOA, PSO, GWO, HHO, DBO, PPO, IWOA, SFOA, Sequoia, HSO, Min-Min, Random |
| **多目标实验** | `createCloudlets` | 对数正态分布 | MO-WOA, MO-PPO, MO-GWO, MO-HHO, MO-DBO, NSGA-II, MOEA/D, SPEA2 |

### 配置步骤

#### 步骤1：修改 SimulationRunner.java

打开 `src/CloudletScheduler/runs/SimulationRunner.java`，找到第33行：

**单目标实验**：
```java
// 单目标实验：使用均匀分布的任务生成器
List<Cloudlet> cloudletList = DatacenterCreator.createCloudlets1(brokerId, rand);
```

**多目标实验**：
```java
// 多目标实验：使用对数正态分布的任务生成器
List<Cloudlet> cloudletList = DatacenterCreator.createCloudlets(brokerId, rand);
```

#### 步骤2：配置算法列表

打开 `src/CloudletScheduler/runs/SchedulerFactory.java`，在 `ALL` 列表中选择要运行的算法：

**单目标算法**（注释掉多目标算法，取消注释单目标算法）：
```java
public static final List<SchedulerFactory> ALL = Arrays.asList(
    // 单目标算法
    new SchedulerFactory("WOA", WOAScheduler::new),
    new SchedulerFactory("IWOA", IWOAScheduler::new),
    new SchedulerFactory("PSO", PSOScheduler::new),
    new SchedulerFactory("GWO", GWOScheduler::new),
    new SchedulerFactory("HHO", HHOScheduler::new),
    new SchedulerFactory("DBO", DBOScheduler::new),
    new SchedulerFactory("PPO", PPOScheduler::new),
    new SchedulerFactory("SFOA", SFOAScheduler::new),
    new SchedulerFactory("sequoia", SequoiaScheduler::new),
    new SchedulerFactory("HSO", HSOScheduler::new)
);
```

**多目标算法**（注释掉单目标算法，保留多目标算法）：
```java
public static final List<SchedulerFactory> ALL = Arrays.asList(
    // 多目标算法
    new SchedulerFactory("MOPPO", MOPPOScheduler::new),
    new SchedulerFactory("MOPPO2", MOPPO2Scheduler::new),
    new SchedulerFactory("MOPPO2E", MOPPO2EnhancedScheduler::new),
    new SchedulerFactory("MOIPPO", MOIPPOScheduler::new),
    new SchedulerFactory("MOWOA", MOWOAScheduler::new),
    new SchedulerFactory("MODBO", MODBOScheduler::new),
    new SchedulerFactory("MOHHO", MOHHOScheduler::new),
    new SchedulerFactory("MOGWO", MOGWOScheduler::new),
    new SchedulerFactory("MOSFOA", MOSFOAScheduler::new),
    new SchedulerFactory("mosequoia", MOSequoiaScheduler::new),
    // 经典多目标算法
    new SchedulerFactory("NSGAII", NSGAIIScheduler::new),
    new SchedulerFactory("MOEAD", MOEADScheduler::new),
    new SchedulerFactory("SPEA2", SPEA2Scheduler::new)
);
```

#### 步骤3：调整实验参数（可选）

根据实验类型调整 `MainRunner.Config` 中的参数：

**单目标实验推荐配置**：
```java
public static class Config {
    public static int CLOUDLET_N = 300;
    public static final int POPULATION = 30;      // 较小的种群
    public static final int MAX_ITER = 500;      // 较少的迭代次数
}
```

**多目标实验推荐配置**：
```java
public static class Config {
    public static int CLOUDLET_N = 300;
    public static final int POPULATION = 50;      // 较大的种群以提高多样性
    public static final int MAX_ITER = 1000;      // 更多的迭代次数以充分探索
    public static final int ARCHIVE_SIZE = 200;   // Pareto存档大小
}
```

### 实验结果差异

**单目标实验输出**：
- `{AlgorithmName}.csv`: 每次试验的 Makespan、TotalTime、LoadBalance、Cost
- `summary_avg.csv`: 所有算法的平均性能对比

**多目标实验输出**：
- `{AlgorithmName}.csv`: 每次试验的 Makespan、TotalTime、LoadBalance、Cost（可能包含多个 Pareto 解的平均值）
- `summary_avg.csv`: 所有算法的平均性能对比
- `pareto_fronts/{AlgorithmName}_trial_{N}_first.csv`: 第一代 Pareto 前沿（4个目标值）
- `pareto_fronts/{AlgorithmName}_trial_{N}_final.csv`: 最终 Pareto 前沿（4个目标值）

### 注意事项

⚠️ **重要提醒**：
1. **必须根据实验类型选择对应的任务生成器**，否则实验结果可能不准确
2. 单目标实验使用 `createCloudlets1`（均匀分布）
3. 多目标实验使用 `createCloudlets`（对数正态分布）
4. 切换实验类型时，记得同时修改 `SimulationRunner.java` 和 `SchedulerFactory.java`
5. 多目标实验的结果包含 Pareto 前沿数据，可用于后续的可视化分析（参考 `draw/` 目录下的脚本）

## 📊 性能评估指标

### 单目标优化指标

| 指标 | 说明 | 计算方式 | 优化目标 |
|------|------|---------|----------|
| **Makespan** | 完成时间 | 所有任务完成的最大时间 | 最小化 |
| **Load Balance** | 负载均衡度 | VM 执行时间的标准差 | 最小化 |
| **Cost** | 总成本 | 所有 VM 的执行成本之和 | 最小化 |
| **TotalTime** | 总执行时间 | 算法运行时间 + 仿真时间 | 最小化 |

### 多目标优化指标（4个目标函数）

多目标优化算法同时优化以下 4 个目标：

| 目标函数 | 说明 | 优化方向 | 计算公式 |
|---------|------|---------|---------|
| **Makespan** | 最大完成时间 | 最小化 | `max(VM完成时间)` |
| **CostEfficiency** | 成本效率比 | 最小化 | `总成本 / 总执行时间` |
| **LoadBalanceIndex** | 负载均衡指数 | 最小化 | `VM执行时间标准差 / 平均执行时间` |
| **ResourceWaste** | 资源浪费率 | 最小化 | `(总分配资源 - 实际使用资源) / 总分配资源` |

**多目标优化的特点**：
- 4 个目标之间存在权衡关系（Trade-off）
- 算法寻找 Pareto 最优解集，而非单一最优解
- 每个 Pareto 解代表一种不同的目标权衡方案
- 用户可以根据实际需求从 Pareto 前沿中选择合适的解

### 计算公式

**负载均衡度（单目标）**：
```
LB = sqrt( Σ(ExecuteTime[i] - AvgExecuteTime)² / VmCount )
```

**总成本（单目标）**：
```
Cost = Σ(ActualCPUTime[i] × CostPerSec[i])
```

**成本效率比（多目标）**：
```
CostEfficiency = TotalCost / TotalExecutionTime
```

**负载均衡指数（多目标）**：
```
LoadBalanceIndex = StdDev(VM执行时间) / Mean(VM执行时间)
```

**资源浪费率（多目标）**：
```
ResourceWaste = (AllocatedResources - UsedResources) / AllocatedResources
```

## 🔬 添加新的调度算法

### 实现单目标调度器

1. 在 `src/CloudletScheduler/Optimizer/` 下创建新目录
2. 实现调度器类继承 `Scheduler` 接口：

```java
public class MyScheduler implements Scheduler {
    private List<Cloudlet> cloudletList;
    private List<Vm> vmList;
    
    public MyScheduler(List<Cloudlet> cloudletList, List<Vm> vmList) {
        this.cloudletList = cloudletList;
        this.vmList = vmList;
    }
    
    @Override
    public void schedule() {
        // 实现调度逻辑
    }
}
```

3. 在 `SchedulerFactory.java` 中注册新算法：

```java
public class SchedulerFactory {
    public static final SchedulerFactory MY_ALGO = 
        new SchedulerFactory("MyAlgorithm", MyScheduler::new);
    
    public static final List<SchedulerFactory> ALL = Arrays.asList(
        // ... 其他算法
        MY_ALGO
    );
}
```

### 实现多目标调度器

参考 `MOOptimizer` 目录下的实现，使用 `ParetoArchive` 管理 Pareto 前沿。

## 📖 关键技术说明

### CloudSim 仿真流程

1. **初始化 CloudSim 环境**：`CloudSim.init()`
2. **创建数据中心**：配置不同性能等级的数据中心
3. **创建虚拟机**：按配置创建异构 VM 列表
4. **创建云任务**：使用对数正态分布生成任务长度
5. **执行调度**：调用调度器分配任务到 VM
6. **启动仿真**：`CloudSim.startSimulation()`
7. **收集结果**：计算 Makespan、LB、Cost 等指标

### 任务生成策略技术细节

项目提供了两种任务生成器，分别使用不同的概率分布来模拟任务特征：

#### `createCloudlets1`（单目标实验）

**分布类型**：均匀随机分布

**参数设置**：
- 执行时间：`length = rand.nextInt(40000) + 10000` → 范围 `[10000, 50000]` MIPS
- 输入文件大小：`fileSize = rand.nextInt(190) + 10` → 范围 `[10, 200]` KB
- 输出文件大小：`outputSize = rand.nextInt(190) + 10` → 范围 `[10, 200]` KB

#### `createCloudlets`（多目标实验）

**分布类型**：对数正态分布（执行时间）+ 正态分布（文件大小）

**参数设置**：
- 执行时间（对数正态分布）：
  ```java
  double mean = 30000.0;
  double variance = 1.5;
  long length = (long) (Math.exp(rand.nextGaussian() * variance + Math.log(mean)));
  ```
- 输入文件大小（正态分布）：`fileSize = max(10, rand.nextGaussian() * 100 + 100)` KB
- 输出文件大小（正态分布）：`outputSize = max(10, rand.nextGaussian() * 100 + 100)` KB

**为什么使用对数正态分布？**
- 真实云环境中的任务执行时间通常呈现长尾分布：大多数任务执行时间较短，少数任务执行时间很长
- 对数正态分布能更好地模拟这种特征，适合多目标优化算法的 Pareto 前沿评估

#### `createCloudletsSCI`（可选，用于特殊场景）

**分布类型**：对数正态分布（执行时间）+ 正态分布（文件大小）

**参数设置**：与 `createCloudlets` 相同，但使用常量定义参数：
- `MEAN_EXEC_TIME = 30000.0`
- `VARIANCE_EXEC_TIME = 1.5`
- `MEAN_FILE_SIZE = 100.0`
- `VARIANCE_FILE_SIZE = 20.0`

**使用场景**：当需要更规范化的参数管理时使用。

### 多目标优化框架

- **Pareto 支配**：解 A 支配解 B，当且仅当 A 在所有目标上不劣于 B，且至少在一个目标上优于 B
- **外部存档（ParetoArchive）**：维护非支配解集合（Pareto 前沿）
  - 自动管理存档大小（默认 200）
  - 使用拥挤度距离维护解的多样性
  - 支持获取第一代和最终代的 Pareto 前沿快照
- **拥挤度距离**：用于维护解的多样性，避免解过度集中
- **4 目标优化**：同时优化 Makespan、CostEfficiency、LoadBalanceIndex、ResourceWaste

### 项目依赖管理

项目使用 Maven 进行依赖管理，主要依赖包括：

- **CloudSim 5.0**：从 JitPack 仓库自动下载
- **Apache Commons Math 3.6.1**

依赖配置在 `pom.xml` 中，编译时会自动下载。

## 📈 结果可视化

项目提供了丰富的 Python 可视化工具，位于 `draw/` 目录：

### 主要可视化脚本

1. **`pareto_front_visualization.ipynb`**：Pareto 前沿可视化
   - 2D/3D Pareto 前沿图
   - 不同算法的 Pareto 前沿对比
   - 第一代 vs 最终代对比

2. **`multi_algorithm_comparison.ipynb`**：多算法综合对比分析
   - 箱线图（Boxplot）对比
   - 热力图（Heatmap）分析
   - 雷达图（Radar Chart）综合性能

3. **`moppo_improvements_analysis.ipynb`**：MO-PPO 系列算法改进效果分析

### 可视化文档

- `Pareto前沿可视化使用说明.md`：Pareto 前沿可视化详细指南
- `MO-PPO改进算法分析说明.md`：MO-PPO 算法改进点分析
- `经典多目标算法说明.md`：NSGA-II、MOEA/D、SPEA2 算法说明
- `BatchRunner使用说明.md`：批量运行器使用指南

### 可视化依赖

```bash
pip install pandas numpy matplotlib seaborn plotly jupyter
```

## 🛠️ 开发指南

### 调试建议

- 设置较小的任务量（如 100）进行快速测试
- 减少试验次数（`TRIALS_PER_EXPERIMENT`）加快调试
- 查看 CloudSim 日志了解仿真详情
- 使用 `Main.java` 进行单次快速测试

### 性能优化

- 调整种群大小和迭代次数平衡性能和时间
- 使用并行化运行多个实验（需修改代码支持）
- 对于大规模任务，考虑增加 JVM 堆内存：`-Xmx4g`
- 使用 `BatchRunner` 进行批量实验时，注意磁盘空间

### 代码结构说明

- **`runs/`**：实验运行框架
  - `MainRunner.java`：标准批量实验运行器
  - `BatchRunner.java`：不同任务数量的批量实验运行器
  - `SimulationRunner.java`：单次仿真运行器
  - `SchedulerFactory.java`：调度器工厂，管理所有算法

- **`datacenter/`**：数据中心和评估函数
  - `Constants.java`：全局常量配置
  - `OptFunction.java`：单目标优化函数
  - `OptFunctionMulti.java`：多目标优化函数接口
  - `MultiObjectiveEvaluator.java`：多目标评估器

- **`strategy/`**：优化策略
  - `chaosMap.java`：混沌映射策略（用于初始化）
  - `mutations.java`：变异策略（用于遗传算法等）

### 添加新算法的步骤

1. **单目标算法**：
   - 在 `Optimizer/` 下创建新目录
   - 实现 `Scheduler` 接口
   - 在 `SchedulerFactory.java` 中注册

2. **多目标算法**：
   - 在 `MOOptimizer/` 下创建新目录
   - 实现多目标优化逻辑，使用 `ParetoArchive` 管理 Pareto 前沿
   - 实现 `Scheduler` 接口，返回 Pareto 存档
   - 在 `SchedulerFactory.java` 中注册


## 📄 许可证

[MIT License](./LICENSE)

## 🤝 贡献者

[@LA4AM12](https://github.com/LA4AM12)

## 📚 相关文档

- **CloudSim 官方文档**：https://github.com/Cloudslab/cloudsim
- **Apache Commons Math 文档**：https://commons.apache.org/proper/commons-math/

---
