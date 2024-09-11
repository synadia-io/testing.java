package io.synadia;

import io.synadia.utils.Debug;
import io.synadia.workloads.*;

public class Runner {

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = new CommandLine(args);
        Workload workload = null;
        switch (commandLine.workload) {
            case "multi":
                workload = new MultiWorkload("Multi", commandLine);
                break;
            case "deployTest":
                workload = new DeployTest(commandLine);
                break;
            case "setupTesting":
                workload = new SetupTesting(commandLine);
                break;
            case "setupTracking":
                workload = new SetupTracking("Setup Tracking", commandLine);
                break;
            case "setupSave":
                workload = new SetupTracking("Setup Save", commandLine);
                break;
            case "watchStats":
                workload = new WatchTracking(WatchTracking.Which.Stats, commandLine);
                break;
            case "watchProfile":
                workload = new WatchTracking(WatchTracking.Which.Profile, commandLine);
                break;
            case "saveTracking":
                workload = new SaveTracking(commandLine);
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
            String wn = workload == null ? "Runner" : workload.label;
            Debug.info(wn, e);
            Debug.stackTrace(wn, e);
            System.exit(-1);
        }
    }
}
