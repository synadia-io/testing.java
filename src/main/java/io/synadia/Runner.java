package io.synadia;

import io.synadia.tools.Debug;
import io.synadia.workloads.DeployTest;
import io.synadia.workloads.Workload;

public class Runner {

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = new CommandLine(args);

        switch (commandLine.workload) {
            case "workload":
            case "publisher":
            case "consumer":
                new Workload(commandLine).runWorkload();
                break;
            case "deployTest":
                new DeployTest(commandLine).runWorkload();
                break;
            default:
                Debug.info("Runner", "Workload not implemented: " + commandLine.workload);
                break;
        }
    }
}
