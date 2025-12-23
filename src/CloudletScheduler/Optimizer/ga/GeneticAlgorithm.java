package CloudletScheduler.Optimizer.ga;

/**
 * @author : LA4AM12
 * @create : 2023-03-12 18:11:52
 * @description : genetic algorithm
 */

import CloudletScheduler.datacenter.OptFunction;

import java.util.*;

public class GeneticAlgorithm {
    /**
     * optFunction: 优化函数，用于评估染色体的适应度。
     * boundary: 边界值，可能用于限制某些参数的范围。
     * population: 种群大小，即每一代中染色体的数量。
     * genesN: 基因数量，每个染色体包含的基因数。
     * maxGenerations: 最大代数，算法运行的最大迭代次数。
     * crossoverRate: 交叉率，决定两个染色体进行交叉的概率。
     * mutationRate: 变异率，决定染色体发生变异的概率。
     * tournamentSize: 锦标赛选择的规模，用于选择下一代的染色体。
     * random: 随机数生成器，用于生成随机数。
     * Chromosomes: 染色体列表，存储当前种群中的所有染色体。
     * bestChromosome: 最佳染色体，存储当前找到的最佳解。
     */
    private OptFunction optFunction;
    private int boundary;
    private int population;
    private int genesN, maxGenerations;
    private double crossoverRate;
    private double mutationRate;
    private int tournamentSize;
    private static final Random random = new Random();
    private List<Chromosome> Chromosomes;
    Chromosome bestChromosome = null;

    public GeneticAlgorithm(OptFunction optFunction, int population, double crossoverRate, double mutationRate, int boundary, int genesN, int tournamentSize, int maxGenerations) {
        this.optFunction = optFunction;
        this.population = population;
        this.crossoverRate = crossoverRate;
        this.mutationRate = mutationRate;
        this.boundary = boundary;
        this.genesN = genesN;
        this.maxGenerations = maxGenerations;
        this.tournamentSize = tournamentSize;
    }

    /**
     * 初始化种群方法
     * 如果Chromosomes数组为空，则创建一个新的种群
     * 种群大小由变量population决定，每个个体的基因长度由变量genesN决定
     * 每个基因取值范围为0到boundary-1
     */
    public void initializePopulation() {
        // 检查Chromosomes是否为null，如果是，则进行初始化
        if (Chromosomes == null) {
            // 创建一个ArrayList来存储染色体
            Chromosomes = new ArrayList<>();
            // 遍历种群大小，创建每个个体
            for (int i = 0; i < population; i++) {
                // 创建一个基因数组，长度为genesN
                int[] genes = new int[genesN];
                // 遍历基因数组，为每个基因赋予随机值
                for (int j = 0; j < genesN; j++) {
                    // 为基因赋予随机值，范围为0到boundary-1
                    genes[j] = random.nextInt(boundary);
                }
                // 将基因数组作为参数创建一个新的染色体对象，并添加到Chromosomes列表中
                Chromosomes.add(new Chromosome(genes));
            }
        }
    }

    public void evaluatePopulation() {
        for (Chromosome chromosome : Chromosomes) {
            double fitness = optFunction.calc(chromosome.getGenes());
            chromosome.setFitness(fitness);
        }
        bestChromosome = Collections.min(Chromosomes);
        // System.out.println(bestChromosome);
    }

    public void evolvePopulation() {
        List<Chromosome> newPopulation = new ArrayList<>();
        for (int i = 0; i < population; i++) {
            Chromosome parent1 = tournamentSelection();
            Chromosome parent2 = tournamentSelection();
            Chromosome offspring = crossover(parent1, parent2);
            mutate(offspring);
            newPopulation.add(offspring);
        }
        Chromosomes = newPopulation;
    }

    // 从种群中选择一个染色体进行锦标赛选择
    public Chromosome tournamentSelection() {
        List<Chromosome> tournament = new ArrayList<>();
        for (int i = 0; i < tournamentSize; i++) {
            int randomIndex = random.nextInt(population);
            tournament.add(Chromosomes.get(randomIndex));
        }
        return Collections.min(tournament);
    }

    // 对两个染色体进行交叉，生成一个新的染色体
    public Chromosome crossover(Chromosome parent1, Chromosome parent2) {
        int[] genes1 = parent1.getGenes();
        int[] genes2 = parent2.getGenes();
        int[] offspringGenes = new int[genesN];
        for (int i = 0; i < genesN; i++) {
            if (random.nextDouble() < crossoverRate) {
                offspringGenes[i] = genes1[i];
            } else {
                offspringGenes[i] = genes2[i];
            }
        }
        return new Chromosome(offspringGenes);
    }

    /**
     * 对染色体进行变异
     * 变异是遗传算法中的一个步骤，通过随机改变染色体中的一个或多个基因来增加遗传多样性
     * 此方法通过遍历染色体的每一个基因，并以一定的概率（mutationRate）随机改变基因的值
     *
     * @param chromosome 要进行变异的染色体对象
     */
    public void mutate(Chromosome chromosome) {
        // 获取染色体的所有基因
        int[] genes = chromosome.getGenes();
        // 遍历每个基因
        for (int i = 0; i < genesN; i++) {
            // 判断当前基因是否需要发生变异
            if (random.nextDouble() < mutationRate) {
                // 以随机值替换当前基因，以实现变异
                genes[i] = random.nextInt(boundary);
            }
        }
        // 将变异后的基因设置回染色体
        chromosome.setGenes(genes);
    }

    // 执行遗传算法
    public int[] run() {
        initializePopulation();
        evaluatePopulation();
        for (int i = 0; i < maxGenerations; i++) {
            evolvePopulation();
            evaluatePopulation();
        }
        return bestChromosome.getGenes();
    }

    // 染色体类
    /**
     * 表示染色体的类，用于遗传算法中表示个体的遗传信息
     * 染色体由一组基因组成，其适应度值用于评估个体在特定环境中的生存能力
     * 该类实现了Comparable接口，以支持基于适应度值对染色体进行比较
     */
    private static class Chromosome implements Comparable<Chromosome> {
        /**
         * 存储染色体的基因序列
         */
        private int[] genes;
        /**
         * 染色体的适应度值，用于评估个体的优劣
         */
        private double fitness;

        /**
         * 构造函数，初始化染色体的基因序列
         *
         * @param genes 染色体的基因序列
         */
        public Chromosome(int[] genes) {
            this.genes = genes;
        }

        /**
         * 获取染色体的基因序列
         *
         * @return 基因序列
         */
        public int[] getGenes() {
            return genes;
        }

        /**
         * 设置染色体的基因序列
         *
         * @param genes 新的基因序列
         */
        public void setGenes(int[] genes) {
            this.genes = genes;
        }

        /**
         * 获取染色体的适应度值
         *
         * @return 适应度值
         */
        public double getFitness() {
            return fitness;
        }

        /**
         * 设置染色体的适应度值
         *
         * @param fitness 新的适应度值
         */
        public void setFitness(double fitness) {
            this.fitness = fitness;
        }

        /**
         * 比较两个染色体的适应度值，用于排序
         *
         * @param other 另一个染色体
         * @return 当前染色体的适应度值与另一个染色体适应度值的比较结果
         */
        @Override
        public int compareTo(Chromosome other) {
            return Double.compare(fitness, other.fitness);
        }

        /**
         * 以字符串形式表示染色体，包括适应度值和基因序列
         *
         * @return 表示染色体的字符串
         */
        @Override
        public String toString() {
            return "fitness:" + fitness + "genes:" + Arrays.toString(genes);
        }
    }
    public void setChromosomes(int[][] chromosomes) {
        Chromosomes = new ArrayList<>();
        for (int[] chromosome : chromosomes) {
            Chromosomes.add(new Chromosome(chromosome));
        }
    }
}

