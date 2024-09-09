/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package io.synadia.workloads;

import io.nats.client.Connection;
import io.nats.client.KeyValue;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.KeyValueWatcher;
import io.nats.client.support.JsonParseException;
import io.nats.client.support.JsonParser;
import io.nats.client.support.JsonValue;
import io.nats.jsmulti.shared.ProfileStats;
import io.nats.jsmulti.shared.Stats;
import io.synadia.CommandLine;
import io.synadia.Workload;
import io.synadia.tools.Debug;
import io.synadia.tools.Displayable;
import io.synadia.tools.WindowDisplay;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static io.synadia.tools.Constants.FINAL;
import static io.synadia.tools.Constants.OS_WIN;

public class WatchTracking extends Workload {
    public enum Which {
        Stats("Stats"),
        Profile("Profile");
        final String workloadName;
        Which(String workloadName) {
            this.workloadName = workloadName;
        }
    }

    private final Which which;
    private final String bucket;
    private final Displayable out;

    public WatchTracking(Which which, CommandLine commandLine) {
        super(which.workloadName, commandLine);
        this.which = which;
        if (which == Which.Stats) {
            bucket = params.statsBucket;
            out = initOutput(1000, 1000);
        }
        else {
            bucket = params.profileBucket;
            out = initOutput(1400, 600);
        }
    }

    private Displayable initOutput(int width, int height) {
        if (params.os.equals(OS_WIN)) {
            return WindowDisplay.instance(workloadName, 100, 100, width, height);
        }

        return new Displayable() {
            @Override
            public void clear() {
                System.out.println("\n\n");
            }

            @Override
            public void print(String s) {
                System.out.print(s);
            }

            @Override
            public void printf(String format, Object... args) {
                System.out.printf(format, args);
            }

            @Override
            public void println() {
                System.out.println();
            }

            @Override
            public void println(String s) {
                System.out.println(s);
            }
        };
    }

    @Override
    public void runWorkload() throws Exception {
        Options options = getAdminOptions();
        try (Connection nc = Nats.connect(options)) {
            KeyValue kv = nc.keyValue(bucket);
            WtWatcher watcher;
            if (which == Which.Stats) {
                watcher = new StatsWatcher();
            }
            else {
                watcher = new ProfileWatcher();
            }
            kv.watchAll(watcher);

            //noinspection InfiniteLoopStatement
            while (true) {
                //noinspection BusyWait
                Thread.sleep(params.watchWaitTime);
                watcher.report();
            }
        }
    }

    class StatsWatcher extends WtWatcher {
        private static final String REPORT_HEAD_LINE   = "| =================== | ================= | =============== | ======================== | ================ |";
        private static final String REPORT_SEP_LINE    = "| ------------------- | ----------------- | --------------- | ------------------------ | ---------------- |";
        private static final String REPORT_LINE_HEADER = "|                     |             count |            time |                 msgs/sec |        bytes/sec |";
        private static final String REPORT_LINE_FORMAT = "| %-19s | %12s msgs | %12s ms | %15s msgs/sec | %12s/sec |\n";

        Map<String, ParsedEntry> map;

        public StatsWatcher() {
            map = new HashMap<>();
        }

        @Override
        void subWatch(ParsedEntry p) {
            p.targetAndLabel(new Stats(p.jv), false);
            map.put(p.label, p);
        }

        @Override
        void subReport() {
            List<ParsedEntry> list = new ArrayList<>(map.values());
            list.sort(Comparator.comparing(p -> p.label));

            out.clear();
            out.println(REPORT_HEAD_LINE);
            out.println(REPORT_LINE_HEADER);
            out.println(REPORT_HEAD_LINE);

            String lastMark = null;
            Stats totalStats = new Stats();
            for (ParsedEntry p : list) {
                boolean alreadyReported = p.reported;
                p.reported = true;
                Stats stats = (Stats)p.target;
                String mark = p.statType + p.contextId;
                if (lastMark == null) {
                    lastMark = mark;
                }
                else if (!lastMark.equals(mark)){
                    lastMark = mark;
                    out.println(REPORT_SEP_LINE);
                    print("Total", totalStats);
                    out.println(REPORT_HEAD_LINE);
                    totalStats = new Stats();
                }
                Stats.totalOne(stats, totalStats);
                print(p.label + (alreadyReported ? "" : "*"), stats);
            }
            out.println(REPORT_SEP_LINE);
            print("Total", totalStats);
            out.println(REPORT_SEP_LINE);
        }

        public void print(String label, Stats stats) {
            long elapsed = stats.getElapsed();
            long messageCount = stats.getMessageCount();
            double messagesPerSecond = elapsed == 0 ? 0 : messageCount * Stats.MILLIS_PER_SECOND / elapsed;
            double bytesPerSecond = Stats.MILLIS_PER_SECOND * (stats.getBytes()) / (elapsed);
            out.printf(REPORT_LINE_FORMAT, label,
                Stats.format(messageCount),
                Stats.format3(elapsed),
                Stats.format3(messagesPerSecond),
                Stats.humanBytes(bytesPerSecond));
        }
    }

    class ProfileWatcher extends WtWatcher {
        Map<String, ParsedEntry> map;

        public ProfileWatcher() {
            map = new HashMap<>();
        }

        @Override
        void subWatch(ParsedEntry p) {
            p.targetAndLabel(new ProfileStats(p.jv), true);
            map.put(p.label, p);
        }

        @Override
        void subReport() {
            out.clear();
            List<ParsedEntry> list = new ArrayList<>(map.values());
            list.sort(Comparator.comparing(p -> p.label));
            String lastMark = null;
            for (int x = 0; x < list.size(); x++) {
                ParsedEntry p = list.get(x);
                ProfileStats ps = (ProfileStats) p.target;
                boolean alreadyReported = p.reported;
                p.reported = true;
                String mark = p.statType;
                if (lastMark == null) {
                    lastMark = mark;
                }
                else if (!lastMark.equals(mark)) {
                    lastMark = mark;
                    out.println(ProfileStats.REPORT_SEP_LINE);
                }
                profileStatsReport(ps, p.label + (alreadyReported ? "" : "*"), x == 0, false);
            }
            out.println(ProfileStats.REPORT_SEP_LINE);
        }
    }

    public void profileStatsReport(ProfileStats p, String label, boolean header, boolean footer) {
        if (header) {
            out.println(ProfileStats.REPORT_SEP_LINE);
            out.printf(ProfileStats.REPORT_LINE_HEADER, "");
            out.println(ProfileStats.REPORT_SEP_LINE);
        }
        out.printf(ProfileStats.REPORT_LINE_FORMAT, label,
            Stats.humanBytes(p.maxMemory),
            Stats.humanBytes(p.heapMax),
            Stats.humanBytes(p.allocatedMemory),
            Stats.humanBytes(p.freeMemory),
            Stats.humanBytes(p.heapUsed),
            Stats.humanBytes(p.heapCommitted),
            Stats.humanBytes(p.nonHeapUsed),
            Stats.humanBytes(p.nonHeapCommitted),
            p.liveThreads.size() + "/" + p.threadCount,
            p.deadThreads.size() + "/" + p.threadCount);

        if (footer) {
            out.println(ProfileStats.REPORT_SEP_LINE);
        }
    }

    abstract class WtWatcher implements KeyValueWatcher {
        private final ReentrantLock lock = new ReentrantLock();
        private boolean changed = false;
        abstract void subWatch(ParsedEntry parsedEntry);

        @Override
        public void watch(KeyValueEntry kve) {
            try {
                lock.lock();
                try {
                    changed = true;
                    subWatch(new ParsedEntry(kve));
                }
                finally {
                    lock.unlock();
                }
            }
            catch (Exception e) {
                Debug.info(workloadName, e);
                Debug.stackTrace(workloadName, e);
                System.exit(-1);
            }
        }

        @Override
        public void endOfData() {
        }

        abstract void subReport();

        void report() {
            lock.lock();
            try {
                if (changed) {
                    changed = false;
                    out.println();
                    subReport();
                }
                else {
                    out.print(".");
                }
            }
            finally {
                lock.unlock();
            }
        }
    }

    static class ParsedEntry {
        final JsonValue jv;
        final boolean fin;
        final String key;
        final String statType;
        final String contextId;
        final String statId;
        String label;
        Object target;
        boolean reported;

        public ParsedEntry(KeyValueEntry kve) throws JsonParseException {
            jv = JsonParser.parse(kve.getValue());

            JsonValue jvFinal = jv.map.get(FINAL);
            if (jvFinal == null) {
                fin = false;
            }
            else {
                fin = jvFinal.bool != null && jvFinal.bool;
            }

            key = kve.getKey();
            String[] parts = key.split("\\.");
            statType = parts[0];
            contextId = parts[1];
            statId = parts.length == 3 ? parts[2] : "";
        }

        static final AtomicInteger CONTEXT_ID = new AtomicInteger(0);
        static final Map<String, Integer> idByContext = new HashMap<>();
        static final Map<Integer, Map<String, Integer>> statIdsForContext = new HashMap<>();
        static final Map<Integer, AtomicInteger> statsIdMakerForContext = new HashMap<>();

        public void targetAndLabel(Object target, boolean contextOnly) {
            this.target = target;
            label = contextOnly
                ? String.format("%s-%03d", statType, getContextCode(this))
                : String.format("%s-%03d-%03d", statType, getContextCode(this), getStatIdCode(this));
        }

        static int getContextCode(ParsedEntry p) {
            Integer cid = idByContext.get(p.contextId);
            if (cid == null) {
                cid = CONTEXT_ID.incrementAndGet();
                idByContext.put(p.contextId, cid);
            }
            return cid;
        }

        static int getStatIdCode(ParsedEntry p) {
            Integer cid = getContextCode(p);
            Map<String, Integer> map = statIdsForContext.computeIfAbsent(cid, k -> new HashMap<>());
            Integer sid = map.get(p.statId);
            if (sid == null) {
                AtomicInteger maker = statsIdMakerForContext.computeIfAbsent(cid, k -> new AtomicInteger());
                sid = maker.incrementAndGet();
                map.put(p.statId, sid);
            }
            return sid;
        }
    }
}
