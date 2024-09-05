// Copyright 2021-2024 The NATS Authors
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

package io.nats.jsmulti.shared;

import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;

import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nats.jsmulti.shared.Utils.makeId;

public class ProfileStats {
    private final String id;
    private String statsAction;
    private String statsKey;

    private long maxMemory;
    private long allocatedMemory;
    private long freeMemory;
    private long heapInit;
    private long heapUsed;
    private long heapCommitted;
    private long heapMax;
    private long nonHeapInit;
    private long nonHeapUsed;
    private long nonHeapCommitted;
    private long nonHeapMax;
    private int threadCount;
    private List<String> deadThreads;
    private List<String> liveThreads;

    private ProfileStats() {
        id = makeId();
        deadThreads = new ArrayList<>();
        liveThreads = new ArrayList<>();
    }

    public ProfileStats(String statsAction, String statsKey) {
        this();
        this.statsAction = statsAction;
        this.statsKey = statsKey;

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        Runtime runtime = Runtime.getRuntime();
        maxMemory = runtime.maxMemory();
        allocatedMemory = runtime.totalMemory();
        freeMemory = runtime.freeMemory();

        MemoryUsage usage = memBean.getHeapMemoryUsage();
        heapInit = usage.getInit();
        heapUsed = usage.getUsed();
        heapCommitted = usage.getCommitted();
        heapMax = usage.getMax();

        usage = memBean.getNonHeapMemoryUsage();
        nonHeapInit = usage.getInit();
        nonHeapUsed = usage.getUsed();
        nonHeapCommitted = usage.getCommitted();
        nonHeapMax = usage.getMax();

        threadCount = threadBean.getThreadCount();
        long[] deadThreadIds = threadBean.findDeadlockedThreads();
        if (deadThreadIds == null) {
            deadThreadIds = new long[0];
        }
        for (long id : threadBean.getAllThreadIds()) {
            String text = "<" + id + "> " + threadBean.getThreadInfo(id).getThreadName();
            if (isAlive(id, deadThreadIds)) {
                liveThreads.add(text);
            }
            else {
                deadThreads.add(text);
            }
        }
    }

    public ProfileStats(JsonValue jv) {
        id = JsonValueUtils.readString(jv, "id", null);
        statsAction = JsonValueUtils.readString(jv, "statsAction", null);
        statsKey = JsonValueUtils.readString(jv, "statsKey", null);
        maxMemory = JsonValueUtils.readLong(jv, "maxMemory", 0);
        allocatedMemory = JsonValueUtils.readLong(jv, "allocatedMemory", 0);
        freeMemory = JsonValueUtils.readLong(jv, "freeMemory", 0);
        heapInit = JsonValueUtils.readLong(jv, "heapInit", 0);
        heapUsed = JsonValueUtils.readLong(jv, "heapUsed", 0);
        heapCommitted = JsonValueUtils.readLong(jv, "heapCommitted", 0);
        heapMax = JsonValueUtils.readLong(jv, "heapMax", 0);
        nonHeapInit = JsonValueUtils.readLong(jv, "nonHeapInit", 0);
        nonHeapUsed = JsonValueUtils.readLong(jv, "nonHeapUsed", 0);
        nonHeapCommitted = JsonValueUtils.readLong(jv, "nonHeapCommitted", 0);
        nonHeapMax = JsonValueUtils.readLong(jv, "nonHeapMax", 0);
        threadCount = JsonValueUtils.readInteger(jv, "threadCount", 0);
        deadThreads = JsonValueUtils.readStringList(jv, "deadThreads");
        liveThreads = JsonValueUtils.readStringList(jv, "liveThreads");
    }

    public Map<String, JsonValue> toJsonValueMap() {
        JsonValueUtils.ArrayBuilder deadBuilder = JsonValueUtils.arrayBuilder();
        for (String s : deadThreads) {
            deadBuilder.add(s);
        }
        JsonValueUtils.ArrayBuilder liveBuilder = JsonValueUtils.arrayBuilder();
        for (String s : liveThreads) {
            liveBuilder.add(s);
        }

        return JsonValueUtils.mapBuilder()
            .put("id", id)
            .put("statsAction", statsAction)
            .put("statsKey", statsKey)
            .put("maxMemory", maxMemory)
            .put("allocatedMemory", allocatedMemory)
            .put("freeMemory", freeMemory)
            .put("heapInit", heapInit)
            .put("heapUsed", heapUsed)
            .put("heapCommitted", heapCommitted)
            .put("heapMax", heapMax)
            .put("nonHeapInit", nonHeapInit)
            .put("nonHeapUsed", nonHeapUsed)
            .put("nonHeapCommitted", nonHeapCommitted)
            .put("nonHeapMax", nonHeapMax)
            .put("threadCount", threadCount)
            .put("deadThreads", deadBuilder.toJsonValue())
            .put("liveThreads", liveBuilder.toJsonValue())
            .toJsonValue().map;
    }

    public String getStatsAction() {
        return statsAction;
    }

    public String getStatsKey() {
        return statsKey;
    }

    private static boolean isAlive(long id, long[] deadThreadIds) {
        for (long dead : deadThreadIds) {
            if (dead == id) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ProfileStats that = (ProfileStats) o;
        return maxMemory == that.maxMemory
            && allocatedMemory == that.allocatedMemory
            && freeMemory == that.freeMemory
            && heapInit == that.heapInit
            && heapUsed == that.heapUsed
            && heapCommitted == that.heapCommitted
            && heapMax == that.heapMax
            && nonHeapInit == that.nonHeapInit
            && nonHeapUsed == that.nonHeapUsed
            && nonHeapCommitted == that.nonHeapCommitted
            && nonHeapMax == that.nonHeapMax
            && threadCount == that.threadCount
            && id.equals(that.id)
            && equivalent(deadThreads, that.deadThreads)
            && equivalent(liveThreads, that.liveThreads);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + Long.hashCode(maxMemory);
        result = 31 * result + Long.hashCode(allocatedMemory);
        result = 31 * result + Long.hashCode(freeMemory);
        result = 31 * result + Long.hashCode(heapInit);
        result = 31 * result + Long.hashCode(heapUsed);
        result = 31 * result + Long.hashCode(heapCommitted);
        result = 31 * result + Long.hashCode(heapMax);
        result = 31 * result + Long.hashCode(nonHeapInit);
        result = 31 * result + Long.hashCode(nonHeapUsed);
        result = 31 * result + Long.hashCode(nonHeapCommitted);
        result = 31 * result + Long.hashCode(nonHeapMax);
        result = 31 * result + threadCount;
        result = 31 * result + deadThreads.hashCode();
        result = 31 * result + liveThreads.hashCode();
        return result;
    }

    private static boolean equivalent(List<String> l1, List<String> l2)
    {
        if (l1 == null || l1.isEmpty()) {
            return l2 == null || l2.isEmpty();
        }

        if (l2 == null || l1.size() != l2.size()) {
            return false;
        }

        for (String s : l1) {
            if (!l2.contains(s)) {
                return false;
            }
        }
        return true;
    }

    private static final String REPORT_SEP_LINE = "| --------------- | ----------- | ----------- | ----------- | ----------- | ----------- | ----------- | ----------- | ----------- | ----------- | ----------- | ----------- | ------- | ------- |";
    private static final String REPORT_LINE_HEADER = "| %-15s | Max         | Allocated   | Free        | Heap Init   | Heap Used   | Heap Cmtd   | Heap Max    | Non Init    | Non Used    | Non Cmtd    | Non Max     | Alive   | Dead    |\n";
    private static final String REPORT_LINE_FORMAT = "| %-15s | %11s | %11s | %11s | %11s | %11s | %11s | %11s | %11s | %11s | %11s | %11s | %7s | %7s |\n";

    public static void report(ProfileStats p, String label, boolean header, boolean footer, PrintStream out) {
        if (header) {
            out.println("\n" + REPORT_SEP_LINE);
            out.printf(REPORT_LINE_HEADER, p.statsAction);
            out.println(REPORT_SEP_LINE);
        }
        out.printf(REPORT_LINE_FORMAT, label,
            Stats.humanBytes(p.maxMemory),
            Stats.humanBytes(p.allocatedMemory),
            Stats.humanBytes(p.freeMemory),
            Stats.humanBytes(p.heapInit),
            Stats.humanBytes(p.heapUsed),
            Stats.humanBytes(p.heapCommitted),
            Stats.humanBytes(p.heapMax),
            Stats.humanBytes(p.nonHeapInit),
            Stats.humanBytes(p.nonHeapUsed),
            Stats.humanBytes(p.nonHeapCommitted),
            Stats.humanBytes(p.nonHeapMax),
            p.liveThreads.size() + "/" + p.threadCount,
            p.deadThreads.size() + "/" + p.threadCount);

        if (footer) {
            out.println(REPORT_SEP_LINE);
        }
    }

    public static ProfileStats total(List<ProfileStats> list) {
        ProfileStats total = new ProfileStats();
        for (ProfileStats p : list) {
            total.maxMemory = Math.max(total.maxMemory, p.maxMemory);
            total.allocatedMemory = Math.max(total.allocatedMemory, p.allocatedMemory);
            total.freeMemory = Math.max(total.freeMemory, p.freeMemory);
            total.heapInit = Math.max(total.heapInit, p.heapInit);
            total.heapUsed = Math.max(total.heapUsed, p.heapUsed);
            total.heapCommitted = Math.max(total.heapCommitted, p.heapCommitted);
            total.heapMax = Math.max(total.heapMax, p.heapMax);
            total.nonHeapInit = Math.max(total.nonHeapInit, p.nonHeapInit);
            total.nonHeapUsed = Math.max(total.nonHeapUsed, p.nonHeapUsed);
            total.nonHeapCommitted = Math.max(total.nonHeapCommitted, p.nonHeapCommitted);
            total.nonHeapMax = Math.max(total.nonHeapMax, p.nonHeapMax);
            total.threadCount = Math.max(total.threadCount, p.threadCount);
            if (p.deadThreads.size() > total.deadThreads.size()) {
                total.deadThreads = p.deadThreads;
            }
            if (p.liveThreads.size() > total.liveThreads.size()) {
                total.liveThreads = p.liveThreads;
            }
        }
        return total;
    }
}
