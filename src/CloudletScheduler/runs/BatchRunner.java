package CloudletScheduler.runs;

import org.cloudbus.cloudsim.Log;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量运行器：按照任务数从100到1000依次增加100来运行实验
 * 
 * 该运行器会循环调用MainRunner的实验逻辑，每次修改任务数量
 */
public class BatchRunner {

    /**
     * 程序入口点
     * 
     * @param args 命令行参数（未使用）
     * @throws Exception 若仿真或文件操作失败
     */
    public static void main(String[] args) throws Exception {
        Log.printLine("=".repeat(80));
        Log.printLine("🚀 Starting BATCH EXPERIMENTS");
        Log.printLine("📋 Task numbers: 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000");
        Log.printLine("=".repeat(80));

        // 任务数列表：从100到1000，每次增加100
        int[] cloudletNumbers = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};

        for (int cloudletNum : cloudletNumbers) {
            Log.printLine("\n" + "=".repeat(80));
            Log.printLine("🔬 Running experiment with " + cloudletNum + " cloudlets");
            Log.printLine("=".repeat(80));

            // 使用反射修改Config.CLOUDLET_N的值
            setCloudletNumber(cloudletNum);

            // 运行一次完整的实验
            runExperimentForCloudletNumber(cloudletNum);
        }

        Log.printLine("\n" + "=".repeat(80));
        Log.printLine("✅ All batch experiments completed!");
        Log.printLine("=".repeat(80));
    }

    /**
     * 使用反射修改MainRunner.Config.CLOUDLET_N的值
     * 
     * @param cloudletNum 新的任务数量
     */
    private static void setCloudletNumber(int cloudletNum) {
        try {
            Field field = MainRunner.Config.class.getDeclaredField("CLOUDLET_N");
            field.setAccessible(true);
            
            // 移除final修饰符
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            
            // 设置新值
            field.set(null, cloudletNum);
            
            Log.printLine("✅ Set CLOUDLET_N to " + cloudletNum);
        } catch (Exception e) {
            Log.printLine("❌ Failed to set CLOUDLET_N: " + e.getMessage());
            throw new RuntimeException("Cannot modify CLOUDLET_N", e);
        }
    }

    /**
     * 运行指定任务数的一次完整实验
     * 
     * @param cloudletNum 任务数量
     * @throws Exception 若仿真或文件操作失败
     */
    private static void runExperimentForCloudletNumber(int cloudletNum) throws Exception {
        int experimentId = getNextExperimentId();
        String experimentDir = MainRunner.Config.BASE_RESULT_DIR + "/run_" + experimentId;
        Files.createDirectories(Paths.get(experimentDir));

        // 在实验目录中记录任务数信息
        try (PrintWriter infoWriter = new PrintWriter(new FileWriter(experimentDir + "/experiment_info.txt"))) {
            infoWriter.println("Experiment ID: " + experimentId);
            infoWriter.println("Cloudlet Number: " + cloudletNum);
            infoWriter.println("Population: " + MainRunner.Config.POPULATION);
            infoWriter.println("Max Iterations: " + MainRunner.Config.MAX_ITER);
            infoWriter.println("Archive Size: " + MainRunner.Config.ARCHIVE_SIZE);
            infoWriter.println("Trials per Experiment: " + MainRunner.Config.TRIALS_PER_EXPERIMENT);
        }

        Log.printLine("🚀 Starting EXPERIMENT #" + experimentId + " (Cloudlets: " + cloudletNum + ")");
        Log.printLine("📁 Results will be saved in: " + experimentDir);
        Log.printLine("🔁 Running " + MainRunner.Config.TRIALS_PER_EXPERIMENT + " trials per scheduler...");

        // 打印表头
        printTrialTableHeader();

        // 执行所有调度器 × 所有 trial 的组合
        for (int trial = 1; trial <= MainRunner.Config.TRIALS_PER_EXPERIMENT; trial++) {
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
        List<MainRunner.AvgResult> avgResults = computeAndSaveAverages(experimentDir);
        
        // 打印最终的平均对比结果
        printFinalComparison(avgResults, experimentId, cloudletNum);
        
        Log.printLine("✅ Experiment #" + experimentId + " completed (Cloudlets: " + cloudletNum + ").");
        Log.printLine("📈 Results saved in: " + experimentDir);
    }

    /**
     * 打印 trial 结果表格的表头
     */
    private static void printTrialTableHeader() {
        System.out.println("\n" + "=".repeat(85));
        System.out.printf("%-6s | %-12s | %10s | %10s | %10s | %12s%n",
                "Trial", "Scheduler", "Makespan", "TotalTime", "LoadBalance", "Cost");
        System.out.println("=".repeat(85));
    }

    /**
     * 将单次 trial 的结果写入对应调度器的 CSV 文件
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
     */
    private static void saveParetoFrontToFile(String experimentDir, String schedulerName,
                                               int trial, CloudletScheduler.MOOptimizer.ParetoArchive archive, 
                                               String generation) throws IOException {
        if (archive == null || archive.isEmpty()) {
            return;
        }
        
        String paretoDir = experimentDir + "/pareto_fronts";
        Files.createDirectories(Paths.get(paretoDir));
        
        String filePath = paretoDir + "/" + schedulerName + "_trial_" + trial + "_" + generation + ".csv";
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("Makespan,CostEfficiency,LoadBalanceIndex,ResourceWaste");
            
            List<CloudletScheduler.datacenter.ObjectiveValues> objectives = archive.getObjectives();
            
            if (objectives == null || objectives.isEmpty()) {
                return;
            }
            
            for (CloudletScheduler.datacenter.ObjectiveValues obj : objectives) {
                if (obj == null || obj.getValues() == null || obj.getValues().length < 4) {
                    continue;
                }
                writer.printf("%.6f,%.6f,%.6f,%.6f%n",
                        obj.getValues()[0],  // makespan
                        obj.getValues()[1],  // costEfficiency
                        obj.getValues()[2],  // loadBalanceIndex
                        obj.getValues()[3]); // resourceWaste
            }
        }
    }

    /**
     * 在控制台打印单次 trial 的结果
     */
    private static void printTrialResult(int trial, String schedulerName, SimulationResult result) {
        System.out.printf("%-6d | %-12s | %10.2f | %10.2f | %10.2f | %12.2f%n",
                trial, schedulerName, result.makespan, result.totalTime, result.loadBalance, result.cost);
    }

    /**
     * 读取每个调度器的所有 trial 数据，计算平均值，并写入 summary_avg.csv
     */
    private static List<MainRunner.AvgResult> computeAndSaveAverages(String experimentDir) throws IOException {
        List<MainRunner.AvgResult> avgResults = new ArrayList<>();
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

                avgResults.add(new MainRunner.AvgResult(factory.name, avgMakespan, avgTotalTime, avgLoadBal, avgCost));
            }
        }

        return avgResults;
    }

    /**
     * 打印最终的平均性能对比表格
     */
    private static void printFinalComparison(List<MainRunner.AvgResult> avgResults, int experimentId, int cloudletNum) {
        System.out.println("\n" + "=".repeat(75));
        System.out.println("📊 FINAL AVERAGE COMPARISON (Experiment #" + experimentId + ", Cloudlets: " + cloudletNum + ")");
        System.out.println("=".repeat(75));
        System.out.printf("%-15s | %12s | %12s | %12s | %12s%n",
                "Scheduler", "AvgMakespan", "AvgTotalTime", "AvgLoadBalance", "AvgCost");
        System.out.println("-".repeat(75));

        for (MainRunner.AvgResult r : avgResults) {
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
     * 获取下一个可用的实验 ID
     */
    private static int getNextExperimentId() {
        File dir = new File(MainRunner.Config.BASE_RESULT_DIR);
        if (!dir.exists()) {
            return 1;
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
