package CloudletScheduler.runs;

import CloudletScheduler.MOOptimizer.modbo.MODBOScheduler;
import CloudletScheduler.MOOptimizer.mogwo.MOGWOScheduler;
import CloudletScheduler.MOOptimizer.mohho.MOHHOScheduler;
import CloudletScheduler.MOOptimizer.moppo.MOPPOScheduler;
import CloudletScheduler.MOOptimizer.moppo2.MOPPO2EnhancedScheduler;
import CloudletScheduler.MOOptimizer.moppo2.MOPPO2Scheduler;
import CloudletScheduler.MOOptimizer.mosequoia.MOSequoiaScheduler;
import CloudletScheduler.MOOptimizer.mosfoa.MOSFOAScheduler;
import CloudletScheduler.MOOptimizer.mowoa.MOWOAScheduler;
import CloudletScheduler.datacenter.Scheduler;

import java.util.Arrays;
import java.util.List;

public class SchedulerFactory {
    public final String name;
    public final SchedulerCreator creator;

    public SchedulerFactory(String name, SchedulerCreator creator) {
        this.name = name;
        this.creator = creator;
    }

    @FunctionalInterface
    public interface SchedulerCreator {
        Scheduler create(List<org.cloudbus.cloudsim.Cloudlet> cloudlets, List<org.cloudbus.cloudsim.Vm> vms);
    }

    public static final List<SchedulerFactory> ALL = Arrays.asList(
//            new SchedulerFactory("WOA", WOAScheduler::new),
//            new SchedulerFactory("IWOA", IWOAScheduler::new),
//            new SchedulerFactory("AWOA", AWOAScheduler::new),
//            new SchedulerFactory("PSO", PSOScheduler::new),
//
//            new SchedulerFactory("DBO", DBOScheduler::new),
//            new SchedulerFactory("HHO", HHOScheduler::new),
//            new SchedulerFactory("GWO", GWOScheduler::new),
//            new SchedulerFactory("PPO", PPOScheduler::new),
//            new SchedulerFactory("SFOA", SFOAScheduler::new),
//            new SchedulerFactory("sequoia", SequoiaScheduler::new),
//            new SchedulerFactory("HSO", HSOScheduler::new)


    /**
     * 多目标优化算法
     */
            new SchedulerFactory("MOPPO", MOPPOScheduler::new),
            new SchedulerFactory("MOPPO2", MOPPO2Scheduler::new),
            new SchedulerFactory("MOPPO2E", MOPPO2EnhancedScheduler::new),

            new SchedulerFactory("MOWOA", MOWOAScheduler::new),
            new SchedulerFactory("MODBO", MODBOScheduler::new),
            new SchedulerFactory("MOHHO", MOHHOScheduler::new),
            new SchedulerFactory("MOGWO", MOGWOScheduler::new),

            new SchedulerFactory("MOSFOA", MOSFOAScheduler::new),
            new SchedulerFactory("mosequoia", MOSequoiaScheduler::new)

//            new SchedulerFactory("maxmin",MaxMinScheduler::new),
//            new SchedulerFactory("minmin",MinMinScheduler::new)
            );
}