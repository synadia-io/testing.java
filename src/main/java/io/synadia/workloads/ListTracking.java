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
import io.synadia.CommandLine;
import io.synadia.Workload;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ListTracking extends Workload {
    public ListTracking(CommandLine commandLine) {
        super("Read Tracking", commandLine);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void runWorkload() throws Exception {
        Options adminOpts = getAdminOptions();
        try (Connection sourceNc = Nats.connect(adminOpts))
        {
            ReadWatcher pw = new ReadWatcher();
            KeyValue kvSourceProfile = sourceNc.keyValue(params.profileBucket);
            kvSourceProfile.watchAll(pw);
            pw.latch.await(5, TimeUnit.SECONDS);

            System.out.println();
            pw.keys.forEach(System.out::println);
        }
    }

    static class ReadWatcher implements KeyValueWatcher {
        final CountDownLatch latch = new CountDownLatch(1);
        final Set<String> keys = new HashSet<>();

        @Override
        public void watch(KeyValueEntry kve) {
            keys.add(kve.getKey());
        }

        @Override
        public void endOfData() {
            latch.countDown();
        }
    }
}
