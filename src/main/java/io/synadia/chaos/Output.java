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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class Output {
    public static final DateTimeFormatter TIME_FORMATTER
        = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final long MESSAGE_DUPE_AGE = 2_000;

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
    public static void message(String... strings) {
        lock.lock();
        try {
            StringBuilder sb = new StringBuilder(TIME_FORMATTER.format(ZonedDateTime.now()));
            for (String s : strings) {
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
        message("ERROR", label, s);
    }

    public static void fatalMessage(String label, String s) {
        message("FATAL", label, s);
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
