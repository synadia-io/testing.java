// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.support.JsonSerializable;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class Output implements ChaosPrinter {

    @Override
    public void out(Object... objects) {
        write(objects);
    }

    @Override
    public void err(Object... objects) {
        write(objects);
    }

    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final long MESSAGE_DUPE_AGE = 5_000;

    static final ReentrantLock lock = new ReentrantLock();
    static final Map<Integer, Long> map = new HashMap<>();
    static final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    static {
        executor.scheduleAtFixedRate(Output::cleanReducer, 5, 5, TimeUnit.MINUTES);
    }

    static void cleanReducer() {
        lock.lock();
        try {
            List<Integer> toRemove = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Map.Entry<Integer, Long> entry : map.entrySet()) {
                long time = entry.getValue();
                long elapsed = now - time;
                if (elapsed > MESSAGE_DUPE_AGE) {
                    toRemove.add(entry.getKey());
                }
            }
            for (Integer key : toRemove) {
                map.remove(key);
            }
        }
        finally {
            lock.unlock();
        }
    }

    static final String NLINDENT = "\n    ";
    public static void write(Object... objects) {
        lock.lock();
        try {
            StringBuilder sb = new StringBuilder(TIME_FORMATTER.format(ZonedDateTime.now()));
            for (Object o : objects) {
                String s = o instanceof String ? (String) o : o.toString();
                if (s.contains("\n")) {
                    if (!s.startsWith("\n")) {
                        sb.append(" | ");
                    }
                    sb.append(s.replace("\n", NLINDENT));
                }
                else {
                    sb.append(" | ");
                    sb.append(s);
                }
            }
            String s = sb.toString();
            int hash = s.hashCode();
            Long time = map.get(hash);
            long now = System.currentTimeMillis();
            long elapsed = time == null ? Long.MAX_VALUE : now - time;
            if (elapsed > MESSAGE_DUPE_AGE) {
                System.out.println(s);
                map.put(hash, now);
            }
        }
        finally {
            lock.unlock();
        }
    }

    public static void errorMessage(String label, String s) {
        write("ERROR", label, s);
    }

    public static void fatalMessage(String label, String s) {
        write("FATAL", label, s);
    }

    public static String FN = "\n  ";
    public static String FBN = "{\n  ";
    public static String formatted(JsonSerializable j) {
        return flat(j).replace("{\"", FBN + "\"").replace(",", "," + FN);
    }

    public static String flat(JsonSerializable j) {
        return j.getClass().getSimpleName() + j.toJson();
    }
}
