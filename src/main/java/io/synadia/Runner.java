package io.synadia;

import io.synadia.workloads.Publisher;

public class Runner {

    public static void main(String[] args) throws Exception {
        args = "--workload publisher --id id1 --params src/main/resources/publish-config.json".split(" ");
        CommandLine commandLine = new CommandLine(args);

        switch (commandLine.workload) {
            case "publisher":
                new Publisher(commandLine).runWorkload();
                break;
            default:
                Debug.info("Runner", "Workload not implemented: " + commandLine.workload);
                break;
        }
    }
}
