// Copyright 2023 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.synadia.chaos;

import io.nats.client.support.JsonSerializable;
import io.nats.client.support.JsonValue;
import io.synadia.chaos.support.CommandLine;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantLock;

public class Output {
    static final ReentrantLock workLock = new ReentrantLock();
    static final ReentrantLock controlLock = new ReentrantLock();
    static final ReentrantLock debugLock = new ReentrantLock();

    static boolean work;
    static boolean debug;

    static boolean started;
    static PrintStream workLog;
    static PrintStream controlLog;
    static PrintStream debugLog;
    static String controlConsoleAreaLabel = null;

    public static void start(CommandLine cmd) {
        if (started) {
            return;
        }

        started = true;
        work = cmd.work;
        debug = cmd.debug;

        if (work || debug) {
            controlConsoleAreaLabel = "CTRL";
        }

        // LOG FILES
        if (cmd.logdir != null) {
            File f = new File(cmd.logdir);
            if (!f.exists() && !f.mkdirs()) {
                errorMessage("OUTPUT", "Unable to create logdir: " + cmd.logdir);
                System.exit(-1);
            }
            try {
                String template = "chaos-app-which-log.txt";
                String fn = template.replace("which", "control");
                Path p = Paths.get(f.getAbsolutePath(), fn);
                controlLog = new PrintStream(new FileOutputStream(p.toFile()));

                if (debug) {
                    fn = template.replace("which", "debug");
                    p = Paths.get(f.getAbsolutePath(), fn);
                    debugLog = new PrintStream(new FileOutputStream(p.toFile()));
                }

                if (work) {
                    fn = template.replace("which", "work");
                    p = Paths.get(f.getAbsolutePath(), fn);
                    workLog = new PrintStream(new FileOutputStream(p.toFile()));
                }
            }
            catch (FileNotFoundException e) {
                errorMessage("OUTPUT", "Unable to create log file: " + e);
                System.exit(-1);
            }
        }
    }

    private static String time() {
        String t = "" + System.currentTimeMillis();
        return t.substring(t.length() - 9);
    }

    public static void workMessage(String label, String s) {
        if (work) {
            workLock.lock();
            try {
                consoleMessage("WORK", label, s);
                if (workLog != null) {
                    consoleMessage(null, label, s, workLog);
                }
            }
            finally {
                workLock.unlock();
            }
        }
    }

    public static void controlMessage(String label, JsonSerializable j) {
        controlMessage(label, formatted(j));
    }

    public static void controlMessage(String label, String jvLabel, JsonValue jv) {
        controlMessage(label, formatted(jv).replace("JsonValue", jvLabel));
    }

    public static void controlMessage(String label, String s) {
        controlLock.lock();
        try {
            consoleMessage(controlConsoleAreaLabel, label, s);
            if (workLog != null) {
                consoleMessage(null, label, s, controlLog);
            }
        }
        finally {
            controlLock.unlock();
        }
    }

    public static void debugMessage(String label, String s) {
        if (debug) {
            debugLock.lock();
            try {
                consoleMessage("DEBUG", label, s);
                if (debugLog != null) {
                    consoleMessage("DEBUG", label, s + "\n", controlLog);
                }
            }
            finally {
                debugLock.unlock();
            }
        }
    }

    static final String NLINDENT = "\n    ";

    public static void errorMessage(String label, String s) {
        consoleMessage("ERROR", label, s, System.out);
    }

    public static void fatalMessage(String label, String s) {
        consoleMessage("FATAL", label, s, System.out);
    }

    public static void consoleMessage(String area, String label, String s) {
        consoleMessage(area, label, s, System.out);
    }

    public static void consoleMessage(String area, String label, String s, PrintStream out) {
        out.print(time());
        String llabel = label == null ? "" : " | " + label;
        out.print(area == null ? llabel : " | " + area + llabel);

        if (s.contains("\n")) {
            if (!s.startsWith("\n")) {
                out.print(" | ");
            }
            out.print(s.replace("\n", NLINDENT));
        }
        else {
            out.print(" | ");
            out.print(s);
        }
        out.println();
    }

    public static String FN = "\n  ";
    public static String FBN = "{\n  ";
    public static String formatted(JsonSerializable j) {
        return j.getClass().getSimpleName() + j.toJson()
            .replace("{\"", FBN + "\"").replace(",", "," + FN);
    }

    public static String flat(JsonSerializable j) {
        return j.getClass().getSimpleName() + j.toJson();
    }

    public static String formatted(Object o) {
        return formatted(o.toString());
    }

    public static String formatted(String s) {
        return s.replace("{", FBN).replace(", ", "," + FN);
    }
}
