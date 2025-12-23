package CloudletScheduler.MOOptimizer.moppo2;

import CloudletScheduler.datacenter.ObjectiveValues;

import java.util.*;
import java.util.stream.IntStream;

/**
 * 维护非支配解集（Pareto Front）
 * 支持拥挤距离、多样性保持、智能 Leader 选择
 */
public class ParetoArchive {
    private final List<double[]> solutions;      // 解向量（实数或离散编码）
    private final List<ObjectiveValues> objectives;
    private final int maxSize;
    private final Random random = new Random();

    public ParetoArchive(int maxSize) {
        if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive");
        this.solutions = new ArrayList<>();
        this.objectives = new ArrayList<>();
        this.maxSize = maxSize;
    }

    /**
     * 尝试添加一个新解。若被支配则丢弃；若支配某些解则替换；否则加入。
     */
    /**
     * 尝试添加一个新解。若被支配则丢弃；若支配某些解则替换；否则加入。
     */
    public void add(double[] position, ObjectiveValues obj) {
        if (position == null || obj == null || obj.getValues() == null)
            throw new IllegalArgumentException("Position or objective values cannot be null");

        List<Integer> toRemove = new ArrayList<>();
        boolean isDominated = false;

        for (int i = 0; i < objectives.size(); i++) {
            int cmp = dominates(obj, objectives.get(i));
            if (cmp == 1) {
                toRemove.add(i);
            } else if (cmp == -1) {
                isDominated = true;
                break;
            }
        }

        if (isDominated) return;

        // 从后往前删除
        for (int i = toRemove.size() - 1; i >= 0; i--) {
            int idx = toRemove.get(i);
            solutions.remove(idx);
            objectives.remove(idx);
        }

        solutions.add(position.clone());
        objectives.add(obj.clone());

        if (solutions.size() > maxSize) {
            truncate();
        }
    }

    /**
     * 判断 a 是否支配 b（所有目标均为 minimization）
     * 返回: 1 = a 支配 b, -1 = b 支配 a, 0 = 互不支配
     */
    private int dominates(ObjectiveValues a, ObjectiveValues b) {
        boolean betterInOne = false;
        for (int i = 0; i < a.values.length; i++) {
            if (a.values[i] > b.values[i]) {
                return -1; // a 在某个目标上更差 → 不支配 b
            }
            if (a.values[i] < b.values[i]) {
                betterInOne = true; // a 在至少一个目标上更好
            }
        }
        return betterInOne ? 1 : 0;
    }

    /**
     * 基于拥挤距离截断存档，保留多样性
     */
    private void truncate() {
        if (solutions.size() <= maxSize) return;

        double[] crowdingDistances = computeCrowdingDistances();
        Integer[] indices = IntStream.range(0, crowdingDistances.length)
                .boxed()
                .toArray(Integer[]::new);

        // 按拥挤距离升序排序（小的先删）
        Arrays.sort(indices, Comparator.comparingDouble(i -> crowdingDistances[i]));

        int toRemoveCount = solutions.size() - maxSize;
        Set<Integer> toRemoveSet = new HashSet<>();
        for (int i = 0; i < toRemoveCount; i++) {
            toRemoveSet.add(indices[i]);
        }

        for (int i = objectives.size() - 1; i >= 0; i--) {
            if (toRemoveSet.contains(i)) {
                solutions.remove(i);
                objectives.remove(i);
            }
        }
    }

    /**
     * 计算每个解的拥挤距离（归一化）
     */
    private double[] computeCrowdingDistances() {
        int n = objectives.size();
        if (n == 0) return new double[0];
        int m = objectives.get(0).values.length;
        double[] distances = new double[n];

        // 对每个目标维度独立处理
        for (int objIdx = 0; objIdx < m; objIdx++) {
            // 获取该目标的所有值
            double[] objVals = new double[n];
            for (int i = 0; i < n; i++) {
                objVals[i] = objectives.get(i).values[objIdx];
            }

            // 排序索引
            Integer[] sortedIndices = IntStream.range(0, n)
                    .boxed()
                    .sorted(Comparator.comparingDouble(i -> objVals[i]))
                    .toArray(Integer[]::new);

            // 边界解赋予无穷大距离
            distances[sortedIndices[0]] = Double.POSITIVE_INFINITY;
            distances[sortedIndices[n - 1]] = Double.POSITIVE_INFINITY;

            double minVal = objVals[sortedIndices[0]];
            double maxVal = objVals[sortedIndices[n - 1]];
            double denom = maxVal - minVal;
            if (denom == 0) denom = 1.0; // 防止除零

            // 中间解计算归一化距离
            for (int i = 1; i < n - 1; i++) {
                double prev = objVals[sortedIndices[i - 1]];
                double next = objVals[sortedIndices[i + 1]];
                if (!Double.isInfinite(distances[sortedIndices[i]])) {
                    distances[sortedIndices[i]] += (next - prev) / denom;
                }
            }
        }

        // 将 INF 转为大数，便于后续处理
        for (int i = 0; i < n; i++) {
            if (Double.isInfinite(distances[i])) {
                distances[i] = 1e10;
            }
        }

        return distances;
    }

    /**
     * 选择 Leader：使用轮盘赌，概率正比于拥挤距离（偏向稀疏区域）
     */
    public double[] selectLeader() {
        if (solutions.isEmpty()) return null;
        if (solutions.size() == 1) return solutions.get(0).clone();

        double[] crowding = computeCrowdingDistances();
        double total = Arrays.stream(crowding).sum();
        if (total <= 0) total = 1.0; // 防止全零

        double r = random.nextDouble() * total;
        double acc = 0.0;
        for (int i = 0; i < crowding.length; i++) {
            acc += crowding[i];
            if (r <= acc) {
                return solutions.get(i).clone();
            }
        }
        // fallback
        return solutions.get(solutions.size() - 1).clone();
    }
//    public double[] selectLeader() {
//        if (solutions.isEmpty()) return null;
//        if (solutions.size() == 1) return solutions.get(0).clone();
//
//        // 👇 新增：找出 makespan 最小的解（假设第0维是makespan）
//        int bestIdx = 0;
//        double bestMakespan = objectives.get(0).getValues()[0];
//        for (int i = 1; i < solutions.size(); i++) {
//            double mk = objectives.get(i).getValues()[0];
//            if (mk < bestMakespan) {
//                bestMakespan = mk;
//                bestIdx = i;
//            }
//        }
//
//        // 如果这个最优解的 makespan 明显优于平均水平，直接选它
//        double avgMakespan = objectives.stream()
//                .mapToDouble(obj -> obj.getValues()[0])
//                .average().orElse(bestMakespan);
//
//        if (bestMakespan <= avgMakespan * 0.95) { // 比平均好5%以上
//            return solutions.get(bestIdx).clone();
//        }
//
//        // 否则走原来的拥挤距离选择
//        double[] crowding = computeCrowdingDistances();
//        double total = Arrays.stream(crowding).sum();
//        if (total <= 0) total = 1.0;
//
//        double r = random.nextDouble() * total;
//        double acc = 0.0;
//        for (int i = 0; i < crowding.length; i++) {
//            acc += crowding[i];
//            if (r <= acc) {
//                return solutions.get(i).clone();
//            }
//        }
//        return solutions.get(solutions.size() - 1).clone();
//    }
    /**
     * 返回目标值之和最小的解（可用于替代“全局最优”）
     */
    public double[] getBestBySum() {
        if (solutions.isEmpty()) return null;
        int bestIdx = 0;
        double minSum = Arrays.stream(objectives.get(0).values).sum();
        for (int i = 1; i < objectives.size(); i++) {
            double sum = Arrays.stream(objectives.get(i).values).sum();
            if (sum < minSum) {
                minSum = sum;
                bestIdx = i;
            }
        }
        return solutions.get(bestIdx).clone();
    }
    public double[] getSolution(int index) {
        if (index >= 0 && index < solutions.size()) {
            return solutions.get(index); // 假设 solutions 是 List<double[]>
        }
        return null;
    }

    // ================== Getters ==================

    public List<double[]> getSolutions() {
        return new ArrayList<>(solutions); // 返回副本，防止外部修改
    }

    public List<ObjectiveValues> getObjectives() {
        return new ArrayList<>(objectives);
    }

    public int size() {
        return solutions.size();
    }

    public boolean isEmpty() {
        return solutions.isEmpty();
    }
    public int getMaxSize() {
        return maxSize;
    }

}