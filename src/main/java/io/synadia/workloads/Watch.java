package io.synadia.workloads;

import io.nats.client.Connection;
import io.nats.client.KeyValue;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.KeyValueWatcher;
import io.nats.client.support.JsonValue;
import io.nats.jsmulti.shared.ProfileStats;
import io.nats.jsmulti.shared.Stats;
import io.synadia.CommandLine;
import io.synadia.ParsedEntry;
import io.synadia.Workload;
import io.synadia.utils.Debug;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static io.synadia.utils.Commons.WATCH;
import static io.synadia.utils.Reporting.*;

public class Watch extends Workload {

    public static final String CV_SOURCE = "CV_SOURCE";
    public static final String CV = "CV";

    private Which which;
    private String bucket;

    private boolean keyIsStats(KeyValueEntry kve) {
        return !kve.getKey().startsWith(CV);
    }

    public void init(CommandLine commandLine) {
        init(commandLine.action, commandLine);
        this.which = Which.instance(WATCH, commandLine.action);
        switch (which){
            case Stats:
            case ReportStats:
                bucket = params.statsBucket;
                break;
            case Profile:
                bucket = params.profileBucket;
                break;
            default:
                throw new RuntimeException("Watch not implemented: " + commandLine.workload);
        }
    }

    @Override
    public void runWorkload() throws Exception {
        Options options = getAdminOptions();
        try (Connection nc = Nats.connect(options)) {
            KeyValue kv = nc.keyValue(bucket);
            WtWatcher watcher = switch (which) {
                case Stats -> new StatsWatcher();
                case ReportStats -> new ReportWatcher();
                case Profile -> new ProfileWatcher();
                default -> throw new RuntimeException("Watch not implemented: " + commandLine.workload);
            };
            kv.watchAll(watcher);

            if (which == Which.ReportStats) {
                String cvs = kv.get(CV_SOURCE).getValueAsString();
                String cv = kv.get(CV).getValueAsString();
                System.out.println(cv + "[" + cvs + "]");
                watcher.report();
                return;
            }

            //noinspection InfiniteLoopStatement
            while (true) {
                //noinspection BusyWait
                Thread.sleep(params.watchWaitTime);
                watcher.report();
            }
        }
    }

    class StatsWatcher extends WtWatcher {
        public StatsWatcher() {
            super(false);
        }

        @Override
        Object extractTarget(JsonValue pjv) {
            return new Stats(pjv);
        }

        @Override
        boolean report(Collection<ParsedEntry> peMapValues) {
            return printStats(peMapValues);
        }
    }

    class ReportWatcher extends WtWatcher {
        public ReportWatcher() {
            super(false);
        }

        @Override
        Object extractTarget(JsonValue pjv) {
            return new Stats(pjv);
        }

        @Override
        boolean report(Collection<ParsedEntry> peMapValues) {
            return summarizeStats(peMapValues);
        }
    }

    class ProfileWatcher extends WtWatcher {
        public ProfileWatcher() {
            super(true);
        }

        @Override
        Object extractTarget(JsonValue pjv) {
            return new ProfileStats(pjv);
        }

        @Override
        boolean report(Collection<ParsedEntry> peMapValues) {
            return printProfileStats(peMapValues);
        }
    }

    abstract class WtWatcher implements KeyValueWatcher {
        protected Map<String, ParsedEntry> peMap;
        private final boolean targetContextOnly;
        private final ReentrantLock rwLock;
        private boolean reportNextTime = false;
        private boolean waiting = false;

        public WtWatcher(boolean targetContextOnly) {
            this.targetContextOnly = targetContextOnly;
            peMap = new HashMap<>();
            rwLock = new ReentrantLock();
        }

        abstract Object extractTarget(JsonValue pjv);
        abstract boolean report(Collection<ParsedEntry> collection);

        @Override
        public void watch(KeyValueEntry kve) {
            try {
                rwLock.lock();
                try {
                    if (keyIsStats(kve)) {
                        reportNextTime = true;
                        ParsedEntry p = new ParsedEntry(kve);
                        p.targetAndLabel(extractTarget(p.jv), targetContextOnly);
                        peMap.put(p.label, p);
                    }
                }
                finally {
                    rwLock.unlock();
                }
            }
            catch (Exception e) {
                Debug.stackTrace(workLabel, e);
                System.exit(-1);
            }
        }

        @Override
        public void endOfData() {}

        void report() {
            rwLock.lock();
            try {
                if (reportNextTime) {
                    if (waiting) {
                        endWait();
                    }
                    reportNextTime = report(peMap.values());
                }
                else {
                    waiting = true;
                    showWait();
                }
            }
            finally {
                rwLock.unlock();
            }
        }
    }
}
