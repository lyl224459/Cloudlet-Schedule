package CloudletScheduler.Optimizer.cco;

import CloudletScheduler.datacenter.OptFunction;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Coral Reef Optimization (CCO) - Java Implementation
 * Based on the provided MATLAB code (custom variant)
 */
public class CoralReefOptimizer {

    private final OptFunction optFunction;
    private final double lb, ub;
    private final int population;
    private final int dim;
    private final int maxIter;

    private double[][] popPos;      // Population positions
    private double[] popFit;        // Population fitness

    private double bestF = Double.POSITIVE_INFINITY;
    private double[] bestX;

    private double[] hisBestFit;
    private final Random random = new Random();

    // CCO-specific parameters
    private static final double ALPHA = 1.34;
    private static final double BETA = 0.3;

    public CoralReefOptimizer(OptFunction optFunction, int population, double lb, double ub, int dim, int maxIter) {
        this.optFunction = optFunction;
        this.population = population;
        this.lb = lb;
        this.ub = ub;
        this.dim = dim;
        this.maxIter = maxIter;

        this.popPos = new double[population][dim];
        this.popFit = new double[population];
        this.bestX = new double[dim];
        this.hisBestFit = new double[maxIter];

        initializePopulation();
    }

    private void initializePopulation() {
        double[] x = new double[population];
        double[] y = new double[population];

        for (int i = 0; i < population; i++) {
            for (int j = 0; j < dim; j++) {
                popPos[i][j] = lb + (ub - lb) * random.nextDouble();
            }
            popFit[i] = evaluate(popPos[i]);

            double theta = (1.0 - 10.0 * (i + 1) / population) * Math.PI;
            double r = ALPHA * Math.exp(BETA * theta / 3.0);
            x[i] = r * Math.cos(theta);
            y[i] = r * Math.sin(theta);

            if (popFit[i] <= bestF) {
                bestF = popFit[i];
                System.arraycopy(popPos[i], 0, bestX, 0, dim);
            }
        }
    }

    private double evaluate(double[] pos) {
        int[] intPos = Arrays.stream(pos)
                .mapToInt(v -> {
                    long r = Math.round(v);
                    if (r < (long) lb) r = (long) lb;
                    if (r > (long) ub) r = (long) ub;
                    return (int) r;
                })
                .toArray();
        return optFunction.calc(intPos);
    }

    private double[] levyFlight(int n, int m) {
        double beta = 1.5;
        double sigmaU = Math.pow(
                gamma(1 + beta) * Math.sin(Math.PI * beta / 2) /
                        (gamma((1 + beta) / 2) * beta * Math.pow(2, (beta - 1) / 2)),
                1.0 / beta
        );
        double[] u = new double[n * m];
        double[] v = new double[n * m];
        for (int i = 0; i < n * m; i++) {
            u[i] = random.nextGaussian() * sigmaU;
            v[i] = random.nextGaussian();
        }
        double[] levy = new double[n * m];
        for (int i = 0; i < n * m; i++) {
            levy[i] = 0.05 * u[i] / Math.pow(Math.abs(v[i]), 1.0 / beta);
        }
        return levy;
    }

    // Simple gamma approximation for small values (not full Gamma function)
    private double gamma(double x) {
        if (x <= 0) return Double.POSITIVE_INFINITY;
        // Use Lanczos approximation or fallback to factorial for integers
        // For simplicity, use Stirling's approx for now (good enough for beta=1.5)
        if (x == 1.5) return Math.sqrt(Math.PI) / 2; // Γ(1.5) = √π / 2
        if (x == 1.0) return 1.0;
        if (x == 2.0) return 1.0;
        return Math.exp((x - 0.5) * Math.log(x) - x + 0.5 * Math.log(2 * Math.PI)); // Stirling
    }

    private void spaceBound(double[] X, double Up, double Low) {
        if (random.nextDouble() < random.nextDouble()) {
            for (int j = 0; j < X.length; j++) {
                if (X[j] > Up || X[j] < Low) {
                    X[j] = Low + (Up - Low) * random.nextDouble();
                }
            }
        } else {
            for (int j = 0; j < X.length; j++) {
                if (X[j] < Low) X[j] = Low;
                if (X[j] > Up) X[j] = Up;
            }
        }
    }

    public int[] execute() {
        int t = 0; // stagnation counter
        int s = 0, z = 0;

        double[] worstX = new double[dim];
        double[] J = new double[population];
        double[] xCoord = new double[population];
        double[] yCoord = new double[population];

        // Precompute spiral coordinates (from init)
        for (int i = 0; i < population; i++) {
            double theta = (1.0 - 10.0 * (i + 1) / population) * Math.PI;
            double r = ALPHA * Math.exp(BETA * theta / 3.0);
            xCoord[i] = r * Math.cos(theta);
            yCoord[i] = r * Math.sin(theta);
        }

        for (int it = 0; it < maxIter; it++) {
            double C = 1.0 - (double) it / maxIter;
            double T = Math.pow(1.0 - Math.sin(Math.PI * it / (2.0 * maxIter)), (double) it / maxIter);

            double die;
            if (t < 15) {
                die = 0.02 * T;
            } else {
                die = 0.02;
                C = 0.8;
            }

            // Find worst individual
            int worstIdx = 0;
            double worstFit = popFit[0];
            for (int i = 1; i < population; i++) {
                if (popFit[i] > worstFit) {
                    worstFit = popFit[i];
                    worstIdx = i;
                }
            }
            System.arraycopy(popPos[worstIdx], 0, worstX, 0, dim);

            // Compute Dis
            double sum = 0.0;
            for (int i = 0; i < population; i++) {
                for (int j = 0; j < dim; j++) {
                    sum += Math.abs((popPos[i][j] - bestX[j]) / (worstX[j] - bestX[j] + 1e-10));
                }
            }
            double Dis = Math.abs(sum / (population * dim));

            double Lx = Math.abs(random.nextGaussian()) * random.nextDouble();

            double[][] newPopPos = new double[population][dim];
            double[] newPopFit = new double[population];

            for (int i = 0; i < population; i++) {
                int Q = 0;
                double F = Math.signum(0.5 - random.nextDouble());
                double E = (nFromT(T) + random.nextDouble()); // n ≈ 1 in original, so use 1
                double[] R1 = randomArray(dim);
                double[] R4 = randomArray(dim);
                double r1 = random.nextDouble();
                double r2 = random.nextDouble();
                double[] S = new double[dim];
                for (int j = 0; j < dim; j++) {
                    S[j] = Math.sin(Math.PI * R4[j] * C);
                }

                // Random permutation of population
                int[] perm = randperm(population);
                double[][] popPosRand = new double[population][dim];
                double[] popFitRand = new double[population];
                for (int k = 0; k < population; k++) {
                    System.arraycopy(popPos[perm[k]], 0, popPosRand[k], 0, dim);
                    popFitRand[k] = popFit[perm[k]];
                }

                J[i] = Math.abs(meanDiff(popPos[i], bestX, worstX));

                if (random.nextDouble() > C) {
                    if (random.nextDouble() > C) {
                        double Cy = 1.0 / (Math.PI * (1 + C * C));
                        if (J[i] > Dis) {
                            for (int j = 0; j < dim; j++) {
                                newPopPos[i][j] = bestX[j] + F * S[j] * (bestX[j] - popPos[i][j]);
                            }
                        } else {
                            if (Dis * Lx < J[i]) {
                                for (int j = 0; j < dim; j++) {
                                    newPopPos[i][j] = bestX[j] * (1 + Math.pow(T, 5) * Cy * E) +
                                            F * S[j] * (bestX[j] - popPos[i][j]);
                                }
                            } else {
                                double[] levy = levyFlight(1, 1);
                                for (int j = 0; j < dim; j++) {
                                    newPopPos[i][j] = bestX[j] * (1 + Math.pow(T, 5) * levy[0]) +
                                            F * S[j] * (bestX[j] - popPos[i][j]);
                                }
                            }
                        }
                    } else {
                        if (random.nextDouble() > C) {
                            if (i % 2 == 0) { // even index (MATLAB is 1-based, Java 0-based → flip logic)
                                double r3 = random.nextDouble();
                                double[] step = new double[dim];
                                for (int j = 0; j < dim; j++) {
                                    step[j] = bestX[j] - E * popPos[i][j];
                                }
                                double[] levy = levyFlight(1, dim);
                                for (int j = 0; j < dim; j++) {
                                    newPopPos[i][j] = C / (it + 1) * (r1 * bestX[j] - r3 * popPos[i][j])
                                            + Math.pow(T, 2) * levy[j] * Math.abs(step[j]);
                                }
                            } else {
                                double[] R2 = randomArray(dim);
                                double[] R3 = randomArray(dim);
                                double[] step = new double[dim];
                                for (int j = 0; j < dim; j++) {
                                    step[j] = popPos[i][j] - E * bestX[j];
                                }
                                double DE = C * F;
                                for (int j = 0; j < dim; j++) {
                                    newPopPos[i][j] = 0.5 * (bestX[j] + popPosRand[0][j]) +
                                            DE * (2 * R1[j] * step[j] - R2[j] / 2.0 * (DE * R3[j] - 1));
                                }
                            }
                        } else {
                            if (random.nextDouble() < random.nextDouble()) {
                                double[] V = new double[dim];
                                if (J[i] < Dis) {
                                    double[] meanPop = mean(popPos);
                                    for (int j = 0; j < dim; j++) {
                                        V[j] = 2 * (random.nextDouble() * (meanPop[j] - popPos[i][j]) +
                                                random.nextDouble() * (bestX[j] - popPos[i][j]));
                                    }
                                } else {
                                    for (int j = 0; j < dim; j++) {
                                        V[j] = 2 * (random.nextDouble() * (popPosRand[1][j] - popPosRand[2][j]) +
                                                random.nextDouble() * (popPosRand[0][j] - popPos[i][j]));
                                    }
                                }
                                double[] step;
                                if (popFit[i] <= popFitRand[i]) {
                                    step = new double[dim];
                                    for (int j = 0; j < dim; j++) {
                                        step[j] = popPos[i][j] - E * popPosRand[i][j];
                                    }
                                    if (i % 2 == 0) {
                                        for (int j = 0; j < dim; j++) {
                                            newPopPos[i][j] = popPos[i][j] + Math.pow(T, 2) * yCoord[i] * (1 - R1[j]) * Math.abs(step[j])
                                                    + F * R1[j] * step[j] / 2.0 + V[j] * J[i] / (it + 1);
                                        }
                                    } else {
                                        for (int j = 0; j < dim; j++) {
                                            newPopPos[i][j] = popPos[i][j] + Math.pow(T, 2) * xCoord[i] * (1 - R1[j]) * Math.abs(step[j])
                                                    + F * R1[j] * step[j] / 2.0 + V[j] * J[i] / (it + 1);
                                        }
                                    }
                                } else {
                                    step = new double[dim];
                                    for (int j = 0; j < dim; j++) {
                                        step[j] = popPosRand[i][j] - E * popPos[i][j];
                                    }
                                    if (i % 2 == 0) {
                                        for (int j = 0; j < dim; j++) {
                                            newPopPos[i][j] = popPosRand[i][j] + Math.pow(T, 2) * yCoord[i] * (1 - R1[j]) * Math.abs(step[j])
                                                    + F * R1[j] * step[j] / 2.0 + V[j] * J[i] / (it + 1);
                                        }
                                    } else {
                                        for (int j = 0; j < dim; j++) {
                                            newPopPos[i][j] = popPosRand[i][j] + Math.pow(T, 2) * xCoord[i] * (1 - R1[j]) * Math.abs(step[j])
                                                    + F * R1[j] * step[j] / 2.0 + V[j] * J[i] / (it + 1);
                                        }
                                    }
                                }
                                s++;
                                if (s > 10) {
                                    int idx1 = random.nextInt(population);
                                    int idx2 = random.nextInt(population);
                                    double[] lesp1 = new double[dim];
                                    for (int j = 0; j < dim; j++) {
                                        lesp1[j] = r1 * popPos[idx1][j] + (1 - r1) * popPos[idx2][j];
                                    }
                                    for (int j = 0; j < dim; j++) {
                                        newPopPos[i][j] = Math.round(lesp1[j]) + F * r1 * R1[j] / Math.pow(it + 1, 4) * newPopPos[i][j];
                                    }
                                    s = 0;
                                }
                            } else {
                                // Sort fitness
                                Integer[] indices = new Integer[population];
                                for (int k = 0; k < population; k++) indices[k] = k;
                                Arrays.sort(indices, (a, b) -> Double.compare(popFit[a], popFit[b]));

                                int A2 = random.nextInt(4) + 1;
                                int A1 = random.nextInt(4) + 1;

                                double[][] D = new double[4][dim];
                                for (int k = 0; k < 3; k++) {
                                    System.arraycopy(popPos[indices[k]], 0, D[k], 0, dim);
                                }
                                double[] meanPop = mean(popPos);
                                System.arraycopy(meanPop, 0, D[3], 0, dim);

                                double[] B = D[A1 - 1]; // A1 in [1,4]

                                double[] Rt1 = new double[dim];
                                double[] Rt2 = new double[dim];
                                for (int j = 0; j < dim; j++) {
                                    Rt1[j] = random.nextInt(360) * Math.PI / 360.0;
                                    Rt2[j] = random.nextInt(360) * Math.PI / 360.0;
                                }

                                double w = 1.0 - Math.pow((Math.exp((double) it / maxIter) - 1) / (Math.exp(1) - 1), 2);

                                if (random.nextDouble() < 0.33) {
                                    for (int j = 0; j < dim; j++) {
                                        newPopPos[i][j] = B[j] + 2 * w * F * Math.cos(Rt1[j]) * Math.sin(Rt2[j]) * (B[j] - popPos[i][j]);
                                    }
                                } else if (random.nextDouble() < 0.33) {
                                    for (int j = 0; j < dim; j++) {
                                        newPopPos[i][j] = B[j] + 2 * w * F * Math.sin(Rt1[j]) * Math.cos(Rt2[j]) * (B[j] - popPos[i][j]);
                                    }
                                } else {
                                    for (int j = 0; j < dim; j++) {
                                        newPopPos[i][j] = B[j] + 2 * w * F * Math.cos(Rt2[j]) * (B[j] - popPos[i][j]);
                                    }
                                }

                                if (A2 == 4) {
                                    Q = 1;
                                }

                                z++;
                                if (z > 5) {
                                    int randIdx = random.nextInt(population);
                                    for (int j = 0; j < dim; j++) {
                                        newPopPos[i][j] = bestX[j] * (1 - (1 - 1.0 / (popPos[randIdx][j] + 1e-10)) * R1[j]);
                                    }
                                    z = 0;
                                }
                            }
                        }
                    }
                } else {
                    if (random.nextDouble() > C) {
                        if (random.nextDouble() > C) {
                            for (int j = 0; j < dim; j++) {
                                newPopPos[i][j] = popPosRand[2][j] + Math.abs(random.nextGaussian()) *
                                        (bestX[j] - popPos[i][j] + popPosRand[0][j] - popPosRand[1][j]);
                            }
                        } else {
                            for (int j = 0; j < dim; j++) {
                                if (random.nextDouble() < random.nextDouble()) {
                                    newPopPos[i][j] = popPosRand[2][j] + Math.abs(random.nextGaussian()) *
                                            (popPosRand[0][j] - popPosRand[1][j]);
                                } else {
                                    newPopPos[i][j] = popPos[i][j];
                                }
                            }
                        }
                    } else {
                        boolean Z1 = random.nextDouble() < random.nextDouble();
                        double term1 = 0, term2 = 0;
                        if (Z1) {
                            term1 = Math.abs(random.nextGaussian()) * ((bestX[0] + popPosRand[0][0]) / 2 - popPosRand[1][0]);
                        }
                        term2 = random.nextDouble() / 2.0 * (popPosRand[2][0] - popPosRand[3][0]);
                        // Simplified: apply same scalar to all dims
                        double delta = term1 + term2;
                        for (int j = 0; j < dim; j++) {
                            newPopPos[i][j] = popPos[i][j] + delta;
                        }
                    }

                    if (random.nextDouble() < C || t > 0.8 * population) {
                        for (int j = 0; j < dim; j++) {
                            if (random.nextDouble() < 0.2 * C + 0.2) {
                                // keep new
                            } else {
                                newPopPos[i][j] = popPos[i][j];
                            }
                        }
                    }
                }

                // Death phase
                if (random.nextDouble() < die) {
                    if (random.nextDouble() > C) {
                        for (int j = 0; j < dim; j++) {
                            newPopPos[i][j] = lb + (ub - lb) * random.nextDouble();
                        }
                    } else {
                        double[] bestVec = new double[dim];
                        double[] levy = levyFlight(1, 1);
                        for (int j = 0; j < dim; j++) {
                            bestVec[j] = bestX[j] * (levy[0] * (r1 > r2 ? 1 : 0) + Math.abs(random.nextGaussian()) * (r1 <= r2 ? 1 : 0));
                        }
                        double Upc = max(bestVec);
                        double Lowc = min(bestVec);
                        for (int j = 0; j < dim; j++) {
                            newPopPos[i][j] = Lowc + (Upc - Lowc) * random.nextDouble();
                        }
                    }
                }

                // Boundary handling
                spaceBound(newPopPos[i], ub, lb);

                // Evaluate
                newPopFit[i] = evaluate(newPopPos[i]);

                // Selection
                if (newPopFit[i] < popFit[i]) {
                    popFit[i] = newPopFit[i];
                    System.arraycopy(newPopPos[i], 0, popPos[i], 0, dim);
                    if (Q == 1) {
                        // Replace worst
                        int worst = argmax(popFit);
                        System.arraycopy(popPos[i], 0, popPos[worst], 0, dim);
                        popFit[worst] = popFit[i];
                    }
                    t = 0;
                } else {
                    t++;
                }

                if (popFit[i] <= bestF) {
                    bestF = popFit[i];
                    System.arraycopy(popPos[i], 0, bestX, 0, dim);
                }
            }

            // Update worst after iteration
            worstIdx = argmax(popFit);
            System.arraycopy(popPos[worstIdx], 0, worstX, 0, dim);

            hisBestFit[it] = bestF;
        }

        return Arrays.stream(bestX)
                .mapToInt(v -> (int) Math.round(v))
                .toArray();
    }

    // =============== Helper Methods ===============

    private double nFromT(double T) {
        return 1.0; // Original uses n=1
    }

    private double meanDiff(double[] a, double[] best, double[] worst) {
        double sum = 0;
        for (int j = 0; j < a.length; j++) {
            sum += (a[j] - best[j]) / (worst[j] - best[j] + 1e-10);
        }
        return sum / a.length;
    }

    private double[] mean(double[][] matrix) {
        double[] res = new double[dim];
        for (double[] row : matrix) {
            for (int j = 0; j < dim; j++) {
                res[j] += row[j];
            }
        }
        for (int j = 0; j < dim; j++) {
            res[j] /= matrix.length;
        }
        return res;
    }

    private double max(double[] arr) {
        double m = arr[0];
        for (double v : arr) if (v > m) m = v;
        return m;
    }

    private double min(double[] arr) {
        double m = arr[0];
        for (double v : arr) if (v < m) m = v;
        return m;
    }

    private int argmax(double[] arr) {
        int idx = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > arr[idx]) idx = i;
        }
        return idx;
    }

    private double[] randomArray(int n) {
        double[] arr = new double[n];
        for (int i = 0; i < n; i++) arr[i] = random.nextDouble();
        return arr;
    }

    private int[] randperm(int n) {
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        List<Integer> perm = IntStream.range(0, n).boxed().collect(Collectors.toList());
        Collections.shuffle(perm, random); // random 是类成员或传入的 Random 实例
        return Arrays.stream(indices).mapToInt(Integer::intValue).toArray();
    }

    // ================== Getters ==================

    public double[] getHisBestFit() {
        return hisBestFit;
    }

    public double getBestF() {
        return bestF;
    }

    public double[] getBestX() {
        return bestX;
    }
}