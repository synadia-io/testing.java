// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.workloads.tps;

import io.nats.client.impl.NoOpStatistics;
import io.synadia.utils.Debug;

import java.util.concurrent.atomic.AtomicInteger;

public class TpsStatsCollector extends NoOpStatistics {
    public static class Collector {
        public long bufferedMessages = 0;
        public long bufferedBytes = 0;
        public long writtenMessages = 0;
        public long writtenBytes = 0;
        public long notWrittenMessages = 0;
        public long notWrittenBytes = 0;
    }

    public final Collector pay;
    public final Collector non;

    public final Collector pay2;
    public final Collector non2;

    public final Collector pay3;
    public final Collector non3;

    public long lastWriteMessages;
    public long lastWriteBytes;

    public final int payloadSize;
    public final AtomicInteger phase;

    public TpsStatsCollector(int payloadSize) {
        pay = new Collector();
        non = new Collector();
        pay2 = new Collector();
        non2 = new Collector();
        pay3 = new Collector();
        non3 = new Collector();
        lastWriteMessages = 0;
        lastWriteBytes = 0;
        this.payloadSize = payloadSize;
        phase = new AtomicInteger(1);
    }

    public void startPhase2() {
        phase.set(2);
    }

    public void startPhase3() {
        phase.set(3);
    }

    @Override
    public void incrementOut(long bytes) {
        try {
            Collector c;
            switch (phase.get()) {
                case 1:
                    if (bytes >= payloadSize) {
                        c = pay;
                    }
                    else {
                        c = non;
                    }
                    break;
                case 2:
                    if (bytes >= payloadSize) {
                        c = pay2;
                    }
                    else {
                        c = non2;
                    }
                    break;
                case 3:
                    if (bytes >= payloadSize) {
                        c = pay3;
                    }
                    else {
                        c = non3;
                    }
                    break;
                default:
                    return;
            }
            c.bufferedMessages++;
            c.bufferedBytes += bytes;
            c.notWrittenMessages++;
            c.notWrittenBytes += bytes;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void registerWrite(long bytes) {
        try {
            Collector cPay;
            Collector cNon;
            switch (phase.get()) {
                case 1:
                    cPay = pay;
                    cNon = non;
                    long notWrittenBytes = cPay.notWrittenBytes + cNon.notWrittenBytes;
                    if (notWrittenBytes > 0 && notWrittenBytes != bytes) {
                        Debug.info("STATS", "MISMATCH %s vs %s", notWrittenBytes, bytes);
                    }
                    lastWriteMessages = cPay.notWrittenMessages + cNon.notWrittenMessages;
                    lastWriteBytes = bytes;
                    break;
                case 2:
                    cPay = pay2;
                    cNon = non2;
                    break;
                case 3:
                    cPay = pay3;
                    cNon = non3;
                    break;
                default:
                    return;
            }

            cPay.writtenMessages += cPay.notWrittenMessages;
            cNon.writtenMessages += cNon.notWrittenMessages;
            cPay.writtenBytes += cPay.notWrittenBytes;
            cNon.writtenBytes += cNon.notWrittenBytes;
            cPay.notWrittenMessages = 0;
            cNon.notWrittenMessages = 0;
            cPay.notWrittenBytes = 0;
            cNon.notWrittenBytes = 0;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
