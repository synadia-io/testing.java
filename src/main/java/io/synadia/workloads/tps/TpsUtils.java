package io.synadia.workloads.tps;

import io.nats.client.Message;

public class TpsUtils {
    public static final String TPS_SENDER = "TPS Sender";
    public static final String TPS_RECEIVER = "TPS Receiver";
    public static String MESSAGE_ID_KEY = "mid";

    public static long extractMessageId(Message msg) {
        //noinspection DataFlowIssue // headers won't be null.
        return Long.parseLong(msg.getHeaders().getFirst(MESSAGE_ID_KEY));
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
