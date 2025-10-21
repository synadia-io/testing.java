package io.synadia.workloads.tps;

import io.nats.client.Message;
import io.nats.client.impl.Headers;

public class TpsUtils {
    public static String MESSAGE_ID_KEY = "mid";

    public static Long extractMessageId(Message msg) {
        Headers headers = msg.getHeaders();
        if (headers != null) {
            String mid = headers.getFirst(MESSAGE_ID_KEY);
            if (mid != null) {
                return Long.parseLong(mid);
            }
        }
        return null;
    }

    @SuppressWarnings("SameParameterValue")
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void sleep(long millis, int nanos) {
        try {
            Thread.sleep(millis, nanos);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
