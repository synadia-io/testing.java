package io.synadia;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueOptions;
import io.nats.client.support.JsonValue;
import io.nats.jsmulti.settings.Context;
import io.nats.jsmulti.shared.Application;
import io.nats.jsmulti.shared.OptionsFactory;
import io.nats.jsmulti.shared.Stats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class TestingApplication implements Application, AutoCloseable {
    String bucket;
    Connection nc;
    KeyValue kv;

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    @Override
    public void init(Context ctx) {
        try {
            nc = ctx.connect(OptionsFactory.OptionsType.ADMIN);
            KeyValueOptions kvo = KeyValueOptions.builder()
                .jetStreamOptions(ctx.getJetStreamOptions())
                .build();
            kv = nc.keyValue(bucket, kvo);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void track(Stats stats, boolean statsAreFinal) {
        String key = stats.key;
        Map<String, JsonValue> map = stats.toJsonValueMap();
        map.put("final", new JsonValue(statsAreFinal));
        JsonValue jv = new JsonValue(map);
        byte[] data = jv.toJson().getBytes(StandardCharsets.US_ASCII);
        if (statsAreFinal) {
            publish(key, data);
        }
        else {
            nc.getOptions().getExecutor().submit(() -> {
                publish(key, data);
            });
        }
    }

    private void publish(String key, byte[] data) {
        try {
            kv.put(key, data);
        }
        catch (IOException | JetStreamApiException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        nc.close();
    }
}
