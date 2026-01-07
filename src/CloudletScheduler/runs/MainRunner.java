package CloudletScheduler.runs;

import org.cloudbus.cloudsim.Log;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 运行器类：负责执行多个调度算法的仿真实验，
 * 每个实验包含若干次 trial（重复运行），并汇总平均性能指标。
 */
public class MainRunner {

    /**
     * 配置常量类：集中管理实验参数。
     */
    public static class Config {
        public static final int CLOUDLET_N = 100;               // 云任务数量
        public static final int NUM_USER = 1;                    // 用户数量
        public static final String BASE_RESULT_DIR = "results";  // 基础结果存储目录
        public static final int TRIALS_PER_EXPERIMENT = 10;       // 每种调度器运行的试验次数
        public static final int POPULATION = 30;
        public static final int MAX_ITER = 500;
        public static final int ARCHIVE_SIZE = 100;
    }

    /**
     * 平均结果数据类：用于封装每个调度器在多次试验后的平均指标。
     */
    static class AvgResult {
        String scheduler;         // 调度器名称
        double avgMakespan;       // 平均完成时间（越小越好）
        double avgLoadBalance;    // 平均负载均衡度（越小越好）
        double avgCost;           // 平均成本（越小越好）
        double avgTotalTime;

        AvgResult(String scheduler, double makespan,double totalTime, double LoadBalance, double cost) {
            this.scheduler = scheduler;
            this.avgMakespan = makespan;
            this.avgTotalTime = totalTime;
            this.avgLoadBalance = LoadBalance;
            this.avgCost = cost;
        }
    }

    /**
     * 程序入口点。
     *
     * @param args 命令行参数（本程序未使用）
     * @throws Exception 若仿真或文件操作失败
     */
    public static void main(String[] args) throws Exception {
        int experimentId = getNextExperimentId();
        String experimentDir = Config.BASE_RESULT_DIR + "/run_" + experimentId;
        Files.createDirectories(Paths.get(experimentDir));

        Log.printLine("🚀 Starting EXPERIMENT #" + experimentId);
        Log.printLine("📁 Results will be saved in: " + experimentDir);
        Log.printLine("🔁 Running " + Config.TRIALS_PER_EXPERIMENT + " trials per scheduler...");

        // 打印表头：实时展示每次 trial 的结果
        printTrialTableHeader();

        // 执行所有调度器 × 所有 trial 的组合
        for (int trial = 1; trial <= Config.TRIALS_PER_EXPERIMENT; trial++) {
            for (SchedulerFactory factory : SchedulerFactory.ALL) {
                // 运行一次仿真
                SimulationResult result = SimulationRunner.runSimulation(factory.creator, trial);

                // 将本次 trial 结果追加写入对应调度器的 CSV 文件
                saveTrialResultToFile(experimentDir, factory.name, trial, result);
                
                // 如果结果包含Pareto存档，保存Pareto前沿数据
                if (result.hasParetoArchive()) {
                    saveParetoFrontToFile(experimentDir, factory.name, trial, result.getParetoArchive(), "final");
                }
                
                // 如果结果包含第一代Pareto存档，保存第一代Pareto前沿数据
                if (result.hasFirstGenerationArchive()) {
                    saveParetoFrontToFile(experimentDir, factory.name, trial, result.getFirstGenerationArchive(), "first");
                }

                // 控制台打印本次 trial 的结果
                printTrialResult(trial, factory.name, result);
            }
        }
        // 计算每个调度器的平均指标，并保存到 summary_avg.csv
        List<AvgResult> avgResults = computeAndSaveAverages(experimentDir);
        // 打印最终的平均对比结果
        printFinalComparison(avgResults, experimentId);
        Log.printLine("✅ Experiment #" + experimentId + " completed.");
        Log.printLine("📈 Results saved in: " + experimentDir);
    }

    /**
     * 打印 trial 结果表格的表头。
     */
    private static void printTrialTableHeader() {
        System.out.println("\n" + "=".repeat(85));
        System.out.printf("%-6s | %-12s | %10s | %10s | %10s | %12s%n",
                "Trial", "Scheduler", "Makespan", "TotalTime", "LoadBalance", "Cost");
        System.out.println("=".repeat(85));
    }

    /**
     * 将单次 trial 的结果写入对应调度器的 CSV 文件。
     *
     * @param experimentDir 实验目录路径
     * @param schedulerName 调度器名称
     * @param trial         当前 trial 编号
     * @param result        仿真结果
     * @throws IOException 文件写入异常
     */
    private static void saveTrialResultToFile(String experimentDir, String schedulerName,
                                              int trial, SimulationResult result) throws IOException {
        String filePath = experimentDir + "/" + schedulerName + ".csv";
        boolean fileExists = new File(filePath).exists();

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath, true))) {
            if (!fileExists) {
                writer.println("Trial,Makespan,TotalTime,LoadBalance,Cost");
            }
            writer.printf("%d,%.6f,%.6f,%.6f,%.6f%n",
                    trial, result.makespan, result.totalTime, result.loadBalance, result.cost);
        }
    }

    /**
     * 保存Pareto前沿数据到CSV文件
     *
     * @param experimentDir 实验目录路径
     * @param schedulerName 调度器名称
     * @param trial         当前 trial 编号
     * @param archive       Pareto存档
     * @param generation    代数标识（"first"或"final"）
     * @throws IOException 文件写入异常
     */
    private static void saveParetoFrontToFile(String experimentDir, String schedulerName,
                                               int trial, CloudletScheduler.MOOptimizer.ParetoArchive archive, String generation) throws IOException {
        // 检查archive是否为空
        if (archive == null || archive.isEmpty()) {
            return; // 如果存档为空，不保存文件
        }
        
        String paretoDir = experimentDir + "/pareto_fronts";
        Files.createDirectories(Paths.get(paretoDir));
        
        String filePath = paretoDir + "/" + schedulerName + "_trial_" + trial + "_" + generation + ".csv";
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("Makespan,Cost,LoadBalance,ResourceUtilization");
            
            List<CloudletScheduler.datacenter.ObjectiveValues> objectives = archive.getObjectives();
            
            if (objectives == null || objectives.isEmpty()) {
                return; // 如果没有目标值，不保存文件
            }
            
            for (CloudletScheduler.datacenter.ObjectiveValues obj : objectives) {
                if (obj == null || obj.getValues() == null || obj.getValues().length < 4) {
                    continue; // 跳过无效的目标值（现在需要4个目标）
                }
                // 多目标优化四个目标：makespan, cost, loadBalance, resourceUtilization
                writer.printf("%.6f,%.6f,%.6f,%.6f%n",
                        obj.getValues()[0],  // makespan
                        obj.getValues()[1],  // cost
                        obj.getValues()[2],  // loadBalance
                        obj.getValues()[3]); // resourceUtilization
            }
        }
    }

    /**
     * 在控制台打印单次 trial 的结果。
     */
    private static void printTrialResult(int trial, String schedulerName, SimulationResult result) {
        // 调整格式：加一列 TotalTime
        System.out.printf("%-6d | %-12s | %10.2f | %10.2f | %10.2f | %12.2f%n",
                trial, schedulerName, result.makespan, result.totalTime, result.loadBalance, result.cost);
    }

    /**
     * 读取每个调度器的所有 trial 数据，计算平均值，并写入 summary_avg.csv。
     *
     * @param experimentDir 实验目录路径
     * @return 各调度器的平均结果列表
     * @throws IOException 文件读写异常
     */
    private static List<AvgResult> computeAndSaveAverages(String experimentDir) throws IOException {
        List<AvgResult> avgResults = new ArrayList<>();
        String summaryPath = experimentDir + "/summary_avg.csv";

        try (PrintWriter summaryWriter = new PrintWriter(new FileWriter(summaryPath))) {
            summaryWriter.println("Scheduler,AvgMakespan,AvgTotalTime,AvgLoadBalance,AvgCost");

            for (SchedulerFactory factory : SchedulerFactory.ALL) {
                String filePath = experimentDir + "/" + factory.name + ".csv";
                double sumMakespan = 0, sumTotalTime = 0, sumLoadBal = 0, sumCost = 0;
                int count = 0;

                try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                    String line;
                    boolean isFirstLine = true;
                    while ((line = reader.readLine()) != null) {
                        if (isFirstLine) {
                            isFirstLine = false;
                            continue;
                        }
                        String[] parts = line.split(",");
                        if (parts.length >= 5) {
                            sumMakespan += Double.parseDouble(parts[1]);
                            sumTotalTime += Double.parseDouble(parts[2]);
                            sumLoadBal += Double.parseDouble(parts[3]);
                            sumCost += Double.parseDouble(parts[4]);
                            count++;
                        }
                    }
                }

                if (count == 0) count = 1;

                double avgMakespan = sumMakespan / count;
                double avgTotalTime = sumTotalTime / count;
                double avgLoadBal = sumLoadBal / count;
                double avgCost = sumCost / count;

                summaryWriter.printf("%s,%.6f,%.6f,%.6f,%.6f%n",
                        factory.name, avgMakespan, avgTotalTime, avgLoadBal, avgCost);

                avgResults.add(new AvgResult(factory.name, avgMakespan, avgTotalTime, avgLoadBal, avgCost));
            }
        }

        return avgResults;
    }

    /**
     * 打印最终的平均性能对比表格。
     *
     * @param avgResults   各调度器的平均结果
     * @param experimentId 当前实验编号
     */
    private static void printFinalComparison(List<AvgResult> avgResults, int experimentId) {
        System.out.println("\n" + "=".repeat(75));
        System.out.println("📊 FINAL AVERAGE COMPARISON (Experiment #" + experimentId + ")");
        System.out.println("=".repeat(75));
        // ✅ 加一列 AvgTotalTime
        System.out.printf("%-15s | %12s | %12s | %12s | %12s%n",
                "Scheduler", "AvgMakespan", "AvgTotalTime", "AvgLoadBalance", "AvgCost");
        System.out.println("-".repeat(75));

        for (AvgResult r : avgResults) {
            System.out.printf("%-15s | %12.2f | %12.2f | %12.4f | %12.2f%n",
                    r.scheduler,
                    r.avgMakespan,
                    r.avgTotalTime,
                    r.avgLoadBalance,
                    r.avgCost);
        }

        System.out.println("=".repeat(75));
    }

    /**
     * 获取下一个可用的实验 ID（基于 results/run_x 目录命名规则）。
     *
     * @return 下一个实验编号（从 1 开始）
     */
    private static int getNextExperimentId() {
        File dir = new File(Config.BASE_RESULT_DIR);
        if (!dir.exists()) {
            return 1; // 若目录不存在，首次实验为 run_1
        }

        int maxId = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && file.getName().startsWith("run_")) {
                    try {
                        int id = Integer.parseInt(file.getName().substring(4));
                        if (id > maxId) maxId = id;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return maxId + 1;
    }
}