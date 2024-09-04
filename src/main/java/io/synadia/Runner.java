package io.synadia;

import io.synadia.tools.Debug;
import io.synadia.workloads.DeployTest;
import io.synadia.workloads.SetupTracking;
import io.synadia.workloads.WatchTracking;

public class Runner {

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = new CommandLine(args);

        try {
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
                case "watchTracking":
                    new WatchTracking(commandLine).runWorkload();
                    break;

                default:
                    Debug.info("Runner", "Workload not implemented: " + commandLine.workload);
                    break;
            }
        }
        catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
