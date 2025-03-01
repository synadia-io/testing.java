package io.synadia.workloads.support;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Options;

import java.io.IOException;

import static io.synadia.utils.Commons.NO_TIX;

public class WorkContext {
    public final Options options;
    public final int tix;
    public final WorkState ws;
    public final Connection nc;
    public final JetStreamManagement jsm;
    public final JetStream js;

    public WorkContext(Options options, Connection nc) throws IOException {
        this(options, nc, NO_TIX, new WorkState());
    }

    public WorkContext(Options options, Connection nc, int tix, WorkState ws) throws IOException {
        this.nc = nc;
        this.ws = ws;
        this.tix = tix;
        this.options = options;
        jsm = nc.jetStreamManagement();
        js = nc.jetStream();
    }

    public long increment() {
        return ws.increment();
    }

    public long elapse() {
        return ws.elapse();
    }
}
