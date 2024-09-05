package io.synadia;

import io.synadia.tools.Debug;
import io.synadia.workloads.DeployTest;
import io.synadia.workloads.SetupTracking;
import io.synadia.workloads.WatchTracking;

public class Runner {

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = new CommandLine(args);
        Workload workload = null;
        switch (commandLine.workload) {
            case "publisher":
                workload = new MultiWorkload("Publisher", commandLine);
                break;
            case "consumer":
                workload = new MultiWorkload("Consumer", commandLine);
                break;
            case "deployTest":
                workload = new DeployTest(commandLine);
                break;
            case "setupTracking":
                workload = new SetupTracking(commandLine);
                break;
            case "watchStats":
                workload = new WatchTracking(WatchTracking.Which.Stats, commandLine);
                break;
            case "watchRunStats":
                workload = new WatchTracking(WatchTracking.Which.RunStats, commandLine);
                break;
            default:
                Debug.info("Runner", "Workload not implemented: " + commandLine.workload);
                break;
        }
        try {
            if (workload != null) {
                workload.runWorkload();
            }
            System.exit(0);
        }
        catch (Exception e) {
            String wn = workload == null ? "Runner" : workload.workloadName;
            Debug.info(wn, e);
            Debug.stackTrace(wn, e);
            System.exit(-1);
        }
    }
}
