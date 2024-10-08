/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package io.synadia.workloads;

import io.nats.client.*;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.KeyValueWatcher;
import io.nats.jsmulti.shared.ProfileStats;
import io.nats.jsmulti.shared.Stats;
import io.synadia.CommandLine;
import io.synadia.ParsedEntry;
import io.synadia.Workload;
import io.synadia.utils.Debug;
import io.synadia.utils.Reporting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SaveTracking extends Workload {
    public SaveTracking(CommandLine commandLine) {
        super("Save Tracking", commandLine);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void runWorkload() throws Exception {

        Options adminOpts = getAdminOptions();
        Options saveOpts = getOptions(params.saveServer);
        try (Connection sourceNc = Nats.connect(adminOpts);
             Connection targetNc = Nats.connect(saveOpts))
        {
            JetStream jsSource = sourceNc.jetStream();
            JetStream jsTarget = targetNc.jetStream();

            KeyValue kvTargetStats = targetNc.keyValue(params.statsBucket);
            KeyValue kvTargetProfile = targetNc.keyValue(params.profileBucket);

            SaveWatcher sw = new SaveWatcher(kvTargetStats);
            SaveWatcher pw = new SaveWatcher(kvTargetProfile);

            KeyValue kvSourceStats = sourceNc.keyValue(params.statsBucket);
            kvSourceStats.watchAll(sw);

            KeyValue kvSourceProfile = sourceNc.keyValue(params.profileBucket);
            kvSourceProfile.watchAll(pw);

            JetStreamSubscription sub = jsSource.subscribe(
                params.profileStreamSubject,
                PushSubscribeOptions.builder().ordered(true).build());
            Thread.sleep(1000); // so I don't have to wait for messages
            Message m = sub.nextMessage(1000);
            while (m != null) {
                Debug.info(label, "profile", m.getSubject());
                jsTarget.publish(m);
                m = sub.nextMessage(1000);
            }

            // 0. Wait
            sw.latch.await(10, TimeUnit.MINUTES);
            pw.latch.await(10, TimeUnit.MINUTES);

            // 1. Label
            sw.list.forEach(p -> p.targetAndLabel(new Stats(p.jv), false));
            pw.list.forEach(p -> p.targetAndLabel(new ProfileStats(p.jv), false));

            // 2. Sort
            ParsedEntry.sort(sw.list);
            ParsedEntry.sort(pw.list);

            // 3. Print
            sw.list.forEach(p -> Reporting.statsLineReport(p.label, (Stats)p.target));
            pw.list.forEach(p -> Reporting.profileLineReport(p.label, (ProfileStats)p.target));
        }
    }

    class SaveWatcher implements KeyValueWatcher {
        final KeyValue kvTarget;
        final CountDownLatch latch;
        final List<ParsedEntry> list;

        public SaveWatcher(KeyValue kvTarget) {
            this.kvTarget = kvTarget;
            this.latch = new CountDownLatch(1);
            this.list = new ArrayList<>();
        }

        @Override
        public void watch(KeyValueEntry kve) {
            try {
                System.out.println(kve.getKey());
                ParsedEntry p = new ParsedEntry(kve);
                list.add(p);
                kvTarget.put(p.key, p.value);
            }
            catch (Exception e) {
                Debug.stackTrace(label, e);
                System.exit(-1);
            }
        }

        @Override
        public void endOfData() {
            latch.countDown();
        }
    }
}
