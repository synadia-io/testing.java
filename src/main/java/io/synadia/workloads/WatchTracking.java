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
import io.nats.client.support.JsonValue;
import io.nats.jsmulti.shared.ProfileStats;
import io.nats.jsmulti.shared.Stats;
import io.synadia.CommandLine;
import io.synadia.ParsedEntry;
import io.synadia.Workload;
import io.synadia.support.Debug;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static io.synadia.support.Reporting.*;

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

    public WatchTracking(Which which, CommandLine commandLine) {
        super(which.workloadName, commandLine);
        this.which = which;
        if (which == Which.Stats) {
            bucket = params.statsBucket;
        }
        else {
            bucket = params.profileBucket;
        }
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
        public StatsWatcher() {
            super(false);
        }

        @Override
        Object extractTarget(JsonValue pjv) {
            return new Stats(pjv);
        }

        @Override
        void report(Collection<ParsedEntry> peMapValues) {
            printStats(peMapValues);
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
        void report(Collection<ParsedEntry> peMapValues) {
            printProfileStats(peMapValues);
        }
    }

    abstract class WtWatcher implements KeyValueWatcher {
        protected Map<String, ParsedEntry> peMap;
        private final boolean targetContextOnly;
        private final ReentrantLock rwLock;
        private boolean changed = false;
        private boolean waiting = false;

        public WtWatcher(boolean targetContextOnly) {
            this.targetContextOnly = targetContextOnly;
            peMap = new HashMap<>();
            rwLock = new ReentrantLock();
        }

        abstract Object extractTarget(JsonValue pjv);
        abstract void report(Collection<ParsedEntry> collection);

        @Override
        public void watch(KeyValueEntry kve) {
            try {
                rwLock.lock();
                try {
                    changed = true;
                    ParsedEntry p = new ParsedEntry(kve);
                    p.targetAndLabel(extractTarget(p.jv), targetContextOnly);
                    peMap.put(p.label, p);
                }
                finally {
                    rwLock.unlock();
                }
            }
            catch (Exception e) {
                Debug.info(label, e);
                Debug.stackTrace(label, e);
                System.exit(-1);
            }
        }

        @Override
        public void endOfData() {}

        void report() {
            rwLock.lock();
            try {
                if (changed) {
                    changed = false;
                    if (waiting) {
                        endWait();
                    }
                    report(peMap.values());
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
