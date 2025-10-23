// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.workloads.tps;

import io.nats.client.impl.NoOpStatistics;
import io.synadia.utils.Debug;

import java.util.concurrent.atomic.AtomicInteger;

import static io.nats.jsmulti.shared.Stats.format3;

public class TpsStatsCollector extends NoOpStatistics {
    public static class Group {
        public long bufferedMessages = 0;
        public long bufferedBytes = 0;
        public long writtenMessages = 0;
        public long writtenBytes = 0;
        public long notWrittenMessages = 0;
        public long notWrittenBytes = 0;

        public void debug(String label, String note) {
            long diff = bufferedMessages - writtenMessages;
            Debug.info(label, note,
                "Buffered vs Socket Messages: %s vs %s ... %s",
                format3(bufferedMessages),
                format3(writtenMessages),
                format3(diff));
        }
    }

    public final Group pay;
    public final Group non;

    public final Group pay2;
    public final Group non2;

    public long lastWriteMessages;
    public long lastWriteBytes;

    public final int payloadSize;
    public final AtomicInteger phase;

    public TpsStatsCollector(int payloadSize) {
        pay = new Group();
        non = new Group();
        pay2 = new Group();
        non2 = new Group();
        lastWriteMessages = 0;
        lastWriteBytes = 0;
        this.payloadSize = payloadSize;
        phase = new AtomicInteger(1);
    }

    public void startPhase2() {
        phase.set(2);
    }

    @Override
    public void incrementOut(long bytes) {
        try {
            Group g;
            switch (phase.get()) {
                case 1:
                    if (bytes >= payloadSize) {
                        g = pay;
                    }
                    else {
                        g = non;
                    }
                    break;
                case 2:
                    if (bytes >= payloadSize) {
                        g = pay2;
                    }
                    else {
                        g = non2;
                    }
                    break;
                default:
                    return;
            }
            g.bufferedMessages++;
            g.bufferedBytes += bytes;
            g.notWrittenMessages++;
            g.notWrittenBytes += bytes;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void registerWrite(long bytes) {
        try {
            Group gPay;
            Group gNon;
            switch (phase.get()) {
                case 1:
                    gPay = pay;
                    gNon = non;
                    long notWrittenBytes = gPay.notWrittenBytes + gNon.notWrittenBytes;
                    if (notWrittenBytes > 0 && notWrittenBytes != bytes) {
                        Debug.info("STATS", "MISMATCH %s vs %s", notWrittenBytes, bytes);
                    }
                    lastWriteMessages = gPay.notWrittenMessages + gNon.notWrittenMessages;
                    lastWriteBytes = bytes;
                    break;
                case 2:
                    gPay = pay2;
                    gNon = non2;
                    break;
                default:
                    return;
            }

            gPay.writtenMessages += gPay.notWrittenMessages;
            gNon.writtenMessages += gNon.notWrittenMessages;
            gPay.writtenBytes += gPay.notWrittenBytes;
            gNon.writtenBytes += gNon.notWrittenBytes;
            gPay.notWrittenMessages = 0;
            gNon.notWrittenMessages = 0;
            gPay.notWrittenBytes = 0;
            gNon.notWrittenBytes = 0;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
