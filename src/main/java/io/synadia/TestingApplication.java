package io.synadia;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueOptions;
import io.nats.client.support.JsonValue;
import io.nats.jsmulti.settings.Context;
import io.nats.jsmulti.shared.Application;
import io.nats.jsmulti.shared.OptionsFactory;
import io.nats.jsmulti.shared.ProfileStats;
import io.nats.jsmulti.shared.Stats;
import io.synadia.tools.Debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import static io.synadia.tools.Constants.*;

public class TestingApplication implements Application, AutoCloseable {
    public static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private Context ctx;
    private Connection nc;
    private String workloadName;
    private KeyValue kvStats;
    private KeyValue kvRunStats;

    public void initTesting(Workload workload) {
        this.workloadName = workload.workloadName;
        try {
            nc = ctx.connect(OptionsFactory.OptionsType.ADMIN);
            KeyValueOptions kvo = KeyValueOptions.builder()
                .jetStreamOptions(ctx.getJetStreamOptions())
                .build();
            kvStats = nc.keyValue(workload.params.statsBucket, kvo);
            kvRunStats = nc.keyValue(workload.params.runStatsBucket, kvo);
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
        byte[] runStatsData = jv.toJson().getBytes(StandardCharsets.US_ASCII);

        if (statsAreFinal) {
            publish(key, statsData, runStatsData);
        }
        else {
            nc.getOptions().getExecutor().submit(() -> {
                publish(key, runStatsData, runStatsData);
            });
        }
    }

    private void publish(String key, byte[] statsData, byte[] runStatsData) {
        try {
            kvStats.put(key, statsData);
            kvRunStats.put(key, runStatsData);
        }
        catch (IOException | JetStreamApiException e) {
            Debug.info(workloadName, "Error publishing tracking.", e);
            Debug.stackTrace(workloadName, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        nc.close();
    }
}
