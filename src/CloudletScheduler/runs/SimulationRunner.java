package CloudletScheduler.runs;

import CloudletScheduler.datacenter.Scheduler;
import CloudletScheduler.datacenter.Type;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.Calendar;
import java.util.List;
import java.util.Random;

public class SimulationRunner {

    public static SimulationResult runSimulation(SchedulerFactory.SchedulerCreator creator, int runIndex) throws Exception {
        Random rand = new Random(runIndex);
        CloudSim.init(MainRunner.Config.NUM_USER, Calendar.getInstance(), false);

        Datacenter dc0 = DatacenterCreator.createDatacenter("DC0", Type.LOW, rand);
        Datacenter dc1 = DatacenterCreator.createDatacenter("DC1", Type.MEDIUM, rand);
        Datacenter dc2 = DatacenterCreator.createDatacenter("DC2", Type.HIGH, rand);

        DatacenterBroker broker = new DatacenterBroker("Broker");
        int brokerId = broker.getId();

        List<Vm> vmList = DatacenterCreator.createVms(brokerId, rand);

        /**
         *  数据集创建引入
         */
        List<Cloudlet> cloudletList = DatacenterCreator.createCloudlets(brokerId, rand);

        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        Scheduler scheduler = creator.create(cloudletList, vmList);
        scheduler.schedule();

        CloudSim.startSimulation();

        List<Cloudlet> finishedCloudlets = broker.getCloudletReceivedList();
        SimulationResult result = ResultAnalyzer.analyzeResults(finishedCloudlets, vmList.size());
        
        // 如果是多目标算法，保存Pareto存档
        if (scheduler.getParetoArchive() != null) {
            result.setParetoArchive(scheduler.getParetoArchive());
        }
        
        // 保存第一代Pareto存档
        if (scheduler.getFirstGenerationArchive() != null) {
            result.setFirstGenerationArchive(scheduler.getFirstGenerationArchive());
        }
        
        return result;
    }
}
