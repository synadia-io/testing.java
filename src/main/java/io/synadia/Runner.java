package io.synadia;

import io.synadia.utils.Debug;
import io.synadia.workloads.*;

public class Runner {

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = new CommandLine(args);
        Workload workload = null;
        switch (commandLine.workload) {
            case "multi":
                workload = new Multi(commandLine);
                break;
            case "deployTest":
                workload = new DeployTest(commandLine);
                break;
            case "setup":
                workload = new Setup(commandLine);
                break;
            case "watch":
                workload = new Watch(commandLine);
                break;
            case "listTracking":
                workload = new ListTracking(commandLine);
                break;
            case "saveTracking":
                workload = new SaveTracking(commandLine);
                break;
            case "chartProfile":
                workload = new ChartProfile(commandLine);
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
            Debug.stackTrace(wn, e);
            System.exit(-1);
        }
    }
}
