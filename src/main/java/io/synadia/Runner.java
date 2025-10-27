package io.synadia;

import io.synadia.utils.Debug;

import java.lang.reflect.Constructor;

public class Runner {

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = new CommandLine(args);
        String className = "io.synadia.workloads." + commandLine.workload;
        try {
            Workload workload = (Workload) classForName(className);
            workload.init(commandLine);
            workload.runWorkload();
            System.exit(0);
        }
        catch (Exception e) {
            Debug.stackTrace("RUNNER", e, commandLine.workload + " [" + className + "]");
            System.exit(-1);
        }
    }

    private static Object classForName(String className) throws ReflectiveOperationException {
        Class<?> c = Class.forName(className);
        Constructor<?> cons = c.getConstructor();
        return cons.newInstance();
    }
}
