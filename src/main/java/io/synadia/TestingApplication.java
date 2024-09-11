package io.synadia;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueOptions;
import io.nats.client.support.JsonValue;
import io.nats.jsmulti.settings.Context;
import io.nats.jsmulti.shared.Application;
import io.nats.jsmulti.shared.OptionsFactory;
import io.nats.jsmulti.shared.ProfileStats;
import io.nats.jsmulti.shared.Stats;
import io.synadia.utils.Debug;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import static io.synadia.utils.Constants.*;

public class TestingApplication implements Application, AutoCloseable {
    public static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private Context ctx;
    private Connection nc;
    private Workload workload;
    private JetStream js;
    private KeyValue kvStats;
    private KeyValue kvRunStats;

    public void initTesting(Workload workload) {
        this.workload = workload;
        try {
            nc = ctx.connect(OptionsFactory.OptionsType.ADMIN);
            js = nc.jetStream(ctx.getJetStreamOptions());
            KeyValueOptions kvo = KeyValueOptions.builder()
                .jetStreamOptions(ctx.getJetStreamOptions())
                .build();
            kvStats = nc.keyValue(workload.params.statsBucket, kvo);
            kvRunStats = nc.keyValue(workload.params.profileBucket, kvo);

            KeyValue kvMulti = nc.keyValue(workload.params.multiBucket, kvo);
            //noinspection DataFlowIssue // action will never be null here
            String key = ctx.action.getLabel() + "."  + ctx.id;
            byte[] value = workload.params.toJson().getBytes(StandardCharsets.US_ASCII);
            kvMulti.put(key, value);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init(Context ctx) {
        try {
            this.ctx = ctx;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void track(Stats stats, boolean statsAreFinal) {
        Date d = new Date();
        JsonValue sTime = new JsonValue(FORMATTER.format(d));
        JsonValue jvTime = new JsonValue(d.getTime());
        String key = stats.key;
        Map<String, JsonValue> map = stats.toJsonValueMap();
        map.put(FINAL, new JsonValue(statsAreFinal));
        map.put(TIME, sTime);
        map.put(TIME_MS, jvTime);
        JsonValue jv = new JsonValue(map);
        byte[] statsData = jv.toJson().getBytes(StandardCharsets.US_ASCII);

        ProfileStats ps = new ProfileStats(ctx.id, stats.action);
        map = ps.toJsonValueMap();
        map.put(TIME, sTime);
        map.put(TIME_MS, jvTime);
        jv = new JsonValue(map);
        byte[] profileData = jv.toJson().getBytes(StandardCharsets.US_ASCII);

        if (statsAreFinal) {
            publish(key, statsData, profileData);
        }
        else {
            nc.getOptions().getExecutor().submit(() -> {
                publish(key, statsData, profileData);
            });
        }
    }

    private void publish(String key, byte[] statsData, byte[] profileData) {
        try {
            kvStats.put(key, statsData);
            kvRunStats.put(key, profileData);
            js.publish(workload.params.profileStreamSubject, profileData);
        }
        catch (Exception e) {
            Debug.info(workload.label, "Error publishing tracking.", e);
            Debug.stackTrace(workload.label, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        nc.close();
    }
}
