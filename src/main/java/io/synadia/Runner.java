package io.synadia;

import io.synadia.tools.Debug;
import io.synadia.workloads.DeployTest;
import io.synadia.workloads.SetupTracking;

public class Runner {

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = new CommandLine(args);

        switch (commandLine.workload) {
            case "publisher":
                new MultiWorkload("Publisher", commandLine).runWorkload();
                break;
            case "consumer":
                new MultiWorkload("Consumer", commandLine).runWorkload();
                break;
            case "deployTest":
                new DeployTest(commandLine).runWorkload();
                break;
            case "setupTracking":
                new SetupTracking(commandLine).runWorkload();
                break;
            default:
                Debug.info("Runner", "Workload not implemented: " + commandLine.workload);
                break;
        }
    }
}
