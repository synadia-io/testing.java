package io.synadia.workloads;

import io.synadia.CommandLine;

public class DebugWorkload extends CustomWorkload {

    @Override
    public void init(CommandLine commandLine) {
        init("Debug Workload", commandLine);
        commandLine.args.add("debug");
        initCustom(new String[0], new String[0]);
    }

    @Override
    protected boolean subRunWorkload(String arg) throws Exception {
        return true;
    }
}
