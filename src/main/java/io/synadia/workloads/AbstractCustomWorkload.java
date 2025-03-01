package io.synadia.workloads;

import io.nats.client.*;
import io.nats.client.api.*;
import io.nats.client.support.JsonValueUtils;
import io.nats.client.support.NatsObjectStoreUtil;
import io.synadia.Workload;
import io.synadia.utils.Debug;
import io.synadia.workloads.support.Event;
import io.synadia.workloads.support.WorkContext;
import io.synadia.workloads.support.WorkState;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

import static io.nats.client.support.JsonUtils.printFormatted;
import static io.nats.jsmulti.shared.Stats.humanBytes;
import static io.nats.jsmulti.shared.Stats.humanTime;
import static io.nats.jsmulti.shared.Utils.sleep;
import static io.synadia.utils.Commons.*;

@SuppressWarnings("SameParameterValue")
public abstract class AbstractCustomWorkload extends Workload {

    public boolean optionsVirtualThreads;
    public String logStreamName;
    public String logSubjectPrefix;
    public String logStreamSubject;
    public String exStreamName;
    public String exSubjectPrefix;
    public String exStreamSubject;
    public long watchFrequency;
    public int progressFrequency;
    public String watchDateFormat;

    public List<String> customStreams;
    public String commandHelp;

    // ----------------------------------------------------------------------------------------------------
    // INITIALIZATION
    // ----------------------------------------------------------------------------------------------------
    protected void initCustom(String[] commands, String[] customStreams) {
        // build help first...
        buildCommandHelp(commands, customStreams);

        // all workloads in this hierarchy expect at least 1 arg...
        if (commandLine.args.isEmpty()) {
            exit("Argument(s) Required");
        }

        this.customStreams = new ArrayList<>(Arrays.asList(customStreams));

        // common
        optionsVirtualThreads = JsonValueUtils.readBoolean(params.jv, "options_virtual_threads", false);
        logStreamName = JsonValueUtils.readString(params.jv, "log_stream_name", "log");
        logSubjectPrefix = JsonValueUtils.readString(params.jv, "log_subject_prefix", "log.");
        logStreamSubject = JsonValueUtils.readString(params.jv, "log_stream_subject", "log.>");
        exStreamName = JsonValueUtils.readString(params.jv, "ex_stream_name", "ex");
        exSubjectPrefix = JsonValueUtils.readString(params.jv, "ex_subject_prefix", "ex.");
        exStreamSubject = JsonValueUtils.readString(params.jv, "ex_stream_subject", "ex.>");
        watchFrequency = JsonValueUtils.readLong(params.jv, "watch_frequency", 5000);
        progressFrequency = JsonValueUtils.readInteger(params.jv, "progress_frequency", 100);
        watchDateFormat = JsonValueUtils.readString(params.jv, "watch_date_format", "HH:mm:ss.SSS");

        Debug.info(workLabel, "optionsVirtualThreads", optionsVirtualThreads);
        Debug.info(workLabel, "logStreamName", logStreamName);
        Debug.info(workLabel, "exStreamName", exStreamName);
        Debug.info(workLabel, "logSubjectPrefix", logSubjectPrefix);
        Debug.info(workLabel, "exSubjectPrefix", exSubjectPrefix);
        Debug.info(workLabel, "logStreamSubject", logStreamSubject);
        Debug.info(workLabel, "exStreamSubject", exStreamSubject);
        Debug.info(workLabel, "watchFrequency", watchFrequency);
        Debug.info(workLabel, "progressFrequency", progressFrequency);
        Debug.info(workLabel, "watchDateFormat", watchDateFormat);
    }

    private void buildCommandHelp(String[] commands, String[] customStreams) {
        StringBuilder ssb = new StringBuilder();
        boolean first = true;
        for (String cs : customStreams) {
            if(first) {
                first = false;
            }
            else {
                ssb.append('|');
            }
            ssb.append(cs);
        }
        ssb.append("|log|ex");

        StringBuilder sb = new StringBuilder();
        sb.append("\n- setup");
        for (String command : commands) {
            sb.append("\n- ");
            sb.append(command);
        }
        sb.append("\n- watch");
        sb.append("\n- stream ").append(ssb);
        sb.append("\n- purge ").append(ssb).append(" [<subject-filter-start>] (defaults to all subjects)");
        sb.append("\n- unex");
        commandHelp = sb.toString();
    }

    protected void exit(String label, Exception e) {
        exit(label + ": " + e.getMessage());
    }

    protected void exit(String reason) {
        Debug.info(workLabel, reason);
        Debug.info(workLabel, "Commands", commandHelp);
        System.exit(0);
    }

    // ----------------------------------------------------------------------------------------------------
    // WORKLOAD START POINT
    // ----------------------------------------------------------------------------------------------------
    @Override
    public void runWorkload() throws Exception {
        String arg = commandLine.args.getFirst();
        switch (arg) {
            case "setup"  -> doSetup();
            case "watch"  -> doWatch();
            case "stream" -> doStream();
            case "purge"  -> doPurge();
            case "unex"   -> doUniqueExceptions();
            default -> {
                if (!subRunWorkload(arg)) {
                    exit("Unknown custom workload command: '" + arg + "'");
                }
            }
        }
    }

    protected boolean subRunWorkload(String arg) throws Exception {
        return false;
    }

    // ----------------------------------------------------------------------------------------------------
    // COMMAND: SETUP
    // ----------------------------------------------------------------------------------------------------
    @SuppressWarnings("SameParameterValue")
    protected void doSetup() throws IOException, JetStreamApiException, InterruptedException {
        doAdmin(wctx -> {
            startJob("Setup");
            startJob("Creating Streams");
            List<String> streamNames = wctx.jsm.getStreamNames();
            for (String streamName : streamNames) {
                wctx.jsm.deleteStream(streamName);
            }
            addStream(wctx.jsm, StreamConfiguration.builder()
                .name(logStreamName)
                .subjects(logStreamSubject)
                .retentionPolicy(RetentionPolicy.Limits)
                .maxAge(Duration.ofMinutes(60))
                .build());
            addStream(wctx.jsm, StreamConfiguration.builder()
                .name(exStreamName)
                .subjects(exStreamSubject)
                .build());
            subDoSetup(wctx.nc, wctx.jsm);
        });
    }

    protected void subDoSetup(Connection nc, JetStreamManagement jsm) throws IOException, JetStreamApiException, InterruptedException {}

    protected static void addStream(JetStreamManagement jsm, StreamConfiguration sc) throws IOException, JetStreamApiException {
        StreamInfo si = jsm.addStream(sc);
        printFormatted(si.getJv());
    }

    // ----------------------------------------------------------------------------------------------------
    // COMMAND: CLEAR
    // ----------------------------------------------------------------------------------------------------
    protected void doPurge() throws IOException, JetStreamApiException, InterruptedException {
        String option = getStringArgFromPosition(2);
        if (option == null || option.isEmpty()) {
            exit("Purge stream not provided");
            return;
        }
        if (option.equals("log")) {
            doPurge(logStreamName);
        }
        else if (option.equals("ex")) {
            doPurge(exStreamName);
        }
        else if (customStreams.contains(option)) {
            doPurge(option);
        }
        else {
            exit("Unknown purge option: '" + option + "'");
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // COMMAND: PURGE
    // ----------------------------------------------------------------------------------------------------
    protected void doPurge(String streamName) throws IOException, JetStreamApiException, InterruptedException {
        doAdmin(wctx -> {
            String filter = getStringArgFromPosition(2);
            if (filter == null || filter.isEmpty()) {
                startJob("Purge " + streamName);
                wctx.nc.jetStreamManagement().purgeStream(streamName);
            }
            else {
                startJob("Purge " + streamName + " (" + filter + ")");
                wctx.jsm.purgeStream(logStreamName, PurgeOptions.builder().subject(filter).build());
            }
        });
    }

    // ----------------------------------------------------------------------------------------------------
    // COMMAND: WATCH
    // ----------------------------------------------------------------------------------------------------
    public static final String SUMMARY_START   = "┌────────────────────────────────────────────────────────────────────────────────────────────┐";
    public static final String SUMMARY_DESC    = "│ Stream Information                                                     " + Debug.rfcTime() + " │";
    public static final String SUMMARY_TOP_SEP = "├──────────────┬────────────┬────────────┬────────────┬────────────┬────────────┬────────────┤";
    public static final String SUMMARY_HEADER  = "│ Stream       │   Messages │   Subjects │  Consumers │  First Seq │   Last Seq │      Bytes │";
    public static final String SUMMARY_SEP     = "├──────────────┼────────────┼────────────┼────────────┼────────────┼────────────┼────────────┤";
    public static final String SUMMARY_FOOT    = "└──────────────┴────────────┴────────────┴────────────┴────────────┴────────────┴────────────┘";
    public static final String SUMMARY_DATA    = "│ %-12s │ %,10d │ %,10d │ %,10d │ %,10d │ %,10d │ %10s │\n";

    public static final String EX_START   = "┌───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐";
    public static final String EX_DESC    = "│ Exceptions                                                                                                                                    │";
    public static final String EX_TOP_SEP = "├────────────────┬────────────────┬─────────────────────┬─────────┬─────────────────────────────────────────────────────────────────────────────┤";
    public static final String EX_HEADER  = "│ ? Job (Thread) │ Exception      │ Last Occurrence     │ Count   │ Message                                                                     │";
    public static final String EX_SEP     = "├────────────────┼────────────────┼─────────────────────┼─────────┼─────────────────────────────────────────────────────────────────────────────┤";
    public static final String EX_FOOT    = "└────────────────┴────────────────┴─────────────────────┴─────────┴─────────────────────────────────────────────────────────────────────────────┘";
    public static final String EX_DATA    = "│ %-14s │ %-14s │ %-17s │ %,7d │ %-75s │\n";
    public static final int EX_WIDTH = 75;

    public static final String LOG_START      = "┌────────────────────────────────────────────────────┐";
    public static final String LOG_DESC       = "│ Log                                                │";
    public static final String LOG_TOP_SEP    = "├───────────────────┬────────────────┬───────────────┤";
    public static final String LOG_HEADER     = "│ ? Job (Thread)    │ Count          │ Elapsed       │";
    public static final String LOG_SEP_LINE   = "├───────────────────┼────────────────┼───────────────┤";
    public static final String LOG_FOOT_LINE  = "└───────────────────┴────────────────┴───────────────┘";
    public static final String LOG_DATA       = "│ %-17s │ %14s │ %-13s │\n";

    public static final String OBJ_START      = "┌────────────────────────────────────────┐";
    public static final String OBJ_DESC       = "│ Object Stores                          │";
    public static final String OBJ_TOP_LINE   = "├──────────────┬────────────┬────────────┤";
    public static final String OBJ_HEADER     = "│ Bucket       │    Objects │     Chunks │";
    public static final String OBJ_SEP_LINE   = "├──────────────┼────────────┼────────────┤";
    public static final String OBJ_FOOT_LINE  = "└──────────────┴────────────┴────────────┘";
    public static final String OBJ_LINE_FMT_1 = "│ %-12s ...";
    public static final String OBJ_LINE_FMT_2 = "\b\b\b│ %,10d │ %,10d │\n";

    @SuppressWarnings("InfiniteLoopStatement")
    protected void doWatch() throws IOException, JetStreamApiException, InterruptedException {
        doAdmin(wctx -> {
            startJob("Watch");
            Map<String, Event> watchMap = new HashMap<>();
            List<String> objectStreams = new ArrayList<>();
            for (String stream : customStreams) {
                if (isOsStream(stream)) {
                    objectStreams.add(stream);
                }
            }
            boolean hasObjectStreams = !objectStreams.isEmpty();
            long freq = getLongArgFromPosition(2, watchFrequency);

            while (true) {
                try {
                    System.out.println("\n\n\n\n");
                    System.out.println(SUMMARY_START);
                    System.out.println(SUMMARY_DESC);

                    // STREAM SUMMARIES
                    System.out.println(SUMMARY_TOP_SEP);
                    System.out.println(SUMMARY_HEADER);
                    System.out.println(SUMMARY_SEP);
                    for (String stream : customStreams) {
                        summarize(wctx.jsm, stream);
                    }
                    System.out.println(SUMMARY_SEP);
                    StreamState exSs = summarize(wctx.jsm, exStreamName);
                    StreamState logSs = summarize(wctx.jsm, logStreamName);
                    System.out.println(SUMMARY_FOOT);

                    watchStream(wctx.jsm, watchMap, true, exStreamName, exSs, v -> {
                        System.out.println(EX_START);
                        System.out.println(EX_DESC);
                    });

                    watchStream(wctx.jsm, watchMap, false, logStreamName, logSs, v -> {
                        System.out.println(LOG_START);
                        System.out.println(LOG_DESC);
                    });

                    if (hasObjectStreams) {
                        System.out.println(OBJ_START);
                        System.out.println(OBJ_DESC);

                        // OBJECT SUMMARIES
                        System.out.println(OBJ_TOP_LINE);
                        System.out.println(OBJ_HEADER);
                        System.out.println(OBJ_SEP_LINE);
                        for (String stream : objectStreams) {
                            summarizeObjectStream(wctx.nc, stream);
                        }
                        System.out.println(OBJ_FOOT_LINE);
                    }
                }
                catch (Exception ignore) {}
                sleep(freq);
            }
        });
    }

    protected static StreamState summarize(JetStreamManagement jsm, String stream) throws IOException, JetStreamApiException {
        StreamInfo si = jsm.getStreamInfo(stream, StreamInfoOptions.allSubjects());
        StreamState ss = si.getStreamState();
        long fseq = si.getStreamState().getFirstSequence();
        long lseq = si.getStreamState().getLastSequence();
        System.out.printf(SUMMARY_DATA, stream, ss.getMsgCount(), ss.getSubjectCount(), ss.getConsumerCount(), fseq, lseq, humanBytes(ss.getByteCount()));
        return ss;
    }

    protected static void summarizeObjectStream(Connection nc, String bucketStreamName) throws IOException, JetStreamApiException {
        String bucketName = NatsObjectStoreUtil.extractBucketName(bucketStreamName);
        StreamContext streamContext = nc.getStreamContext(bucketStreamName);
        String filter = NatsObjectStoreUtil.toMetaStreamSubject(bucketName);
        OrderedConsumerContext occ = streamContext.createOrderedConsumer(
            new OrderedConsumerConfiguration().filterSubject(filter));

        System.out.printf(OBJ_LINE_FMT_1, bucketName);
        long chunks = 0;
        long items = 0;
        try (IterableConsumer it = occ.iterate()) {
            Message m = it.nextMessage(5000);
            while (m != null) {
                ObjectInfo oi = new ObjectInfo(m);
                if (!oi.isDeleted()) {
                    items++;
                    chunks += oi.getChunks();
                }
                m = it.nextMessage(1000);
            }
            System.out.printf(OBJ_LINE_FMT_2, items, chunks);
        }
        catch (Exception ignore) {
            System.out.printf(OBJ_LINE_FMT_2, -1, -1);
        }
    }

    protected void watchStream(JetStreamManagement jsm, Map<String, Event> watchMap, boolean isEx, String streamName, StreamState ss, java.util.function.Consumer<Void> beforeFirst) {
        Map<String, Long> map = new HashMap<>();
        List<String> sorted = new ArrayList<>();
        for (Subject subject : ss.getSubjects()) {
            sorted.add(subject.getName());
            map.put(subject.getName(), subject.getCount());
        }
        Collections.sort(sorted);

        boolean first = true;
        for (String subject : sorted) {
            try {
                MessageInfo mi = jsm.getLastMessage(streamName, subject);
                if (mi != null) {
                    if (first) {
                        first = false;
                        beforeFirst.accept(null);
                        if (isEx) {
                            System.out.println(EX_TOP_SEP);
                            System.out.println(EX_HEADER);
                            System.out.println(EX_SEP);
                        }
                        else {
                            System.out.println(LOG_TOP_SEP);
                            System.out.println(LOG_HEADER);
                            System.out.println(LOG_SEP_LINE);
                        }
                    }
                    Event prev = watchMap.get(subject);
                    Event event = new Event(this, mi.getData());
                    watchMap.put(subject, event);
                    String job = (prev == null || !prev.equals(event) ? "* " : "  ") + event.job;
                    if (event.tix != NO_TIX) {
                        job = job + " (" + event.tix + ")";
                    }

                    String ht = humanTime(event.elapsed);

                    if (isEx) {
                        String time = Debug.rfcTime(mi.getTime());
                        long count = map.get(subject);
                        String deets = event.exceptionMessage == null ? "" : event.exceptionMessage;
                        if (deets.length() > EX_WIDTH) {
                            deets = deets.substring(0, EX_WIDTH - 3) + "...";
                        }
                        System.out.printf(EX_DATA, job, event.qualifier, time, count, deets);
                    }
                    else {
                        String cnt = "";
                        if (event.count > 0) {
                            cnt = String.format("%,d", event.count);
                        }
                        System.out.printf(LOG_DATA, job, cnt, ht);
                    }
                }
            }
            catch (Exception e) {
                System.out.println("INTERNAL ERROR: " + e);
            }
        }

        if (!first) {
            System.out.println(isEx ? EX_FOOT : LOG_FOOT_LINE);
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // COMMAND: STREAM
    // ----------------------------------------------------------------------------------------------------
    protected void doStream() throws Exception {
        startJob("Stream");
        String streamName = getStringArgFromPosition(2);
        if (streamName == null || streamName.isEmpty()) {
            exit("Stream not provided");
        }
        doStream(streamName);
    }

    @SuppressWarnings("InfiniteLoopStatement")
    protected void doStream(String streamName) throws IOException, JetStreamApiException, InterruptedException {
        doAdmin(wctx -> {
            StreamInfo si = wctx.jsm.getStreamInfo(streamName);
            long seq = si.getStreamState().getFirstSequence();
            seq = getLongArgFromPosition(3, seq);
            long last = si.getStreamState().getLastSequence();
            int tracker = 0;
            while (true) {
                if (seq > last) {
                    si = wctx.jsm.getStreamInfo(streamName);
                    long currentLast = si.getStreamState().getLastSequence();
                    last = si.getStreamState().getLastSequence();
                    if (currentLast <= last) {
                        sleep(1000);
                        continue;
                    }
                }
                try {
                    MessageInfo mi = wctx.jsm.getNextMessage(streamName, seq, ">");
                    seq = mi.getSeq() + 1;
                    byte[] data = mi.getData();
                    String sdata = "<no data>";
                    if (data != null && data.length > 0) {
                        sdata = new String(data);
                    }
                    if (tracker > 0) {
                        System.out.println();
                    }
                    System.out.println(mi.getSeq() + " | " + mi.getSubject() + " | " + sdata);
                    tracker = 0;
                }
                catch (JetStreamApiException e) {
                    if (e.getMessage().contains("10037")) { // it's fine the message is gone
                        if (++tracker % progressFrequency == 0) {
                            System.out.println();
                        }
                        else {
                            System.out.print('x');
                        }
                    }
                    else {
                        throw e;
                    }
                }
            }
        });
    }

    // ----------------------------------------------------------------------------------------------------
    // COMMAND: UNIQUE
    // ----------------------------------------------------------------------------------------------------
    protected void doUniqueExceptions() throws JetStreamApiException, IOException, InterruptedException {
        String streamName = getStringArgFromPosition(2);
        doUniqueExceptions(streamName == null || streamName.isEmpty() ? exStreamName : streamName);
    }

    static class Unex {
        final String key;
        Event event;
        long count;
        ZonedDateTime time;

        Unex(AbstractCustomWorkload acw, Message m) {
            event = new Event(acw, m.getData());
            count = 1;
            time = m.metaData().timestamp();
            key = event.subject() + event.job + event.exceptionClass + event.exceptionMessage;
        }

        void increment(Unex unex) {
            count++;
            event = unex.event;
            time = unex.time;
        }
    }

    protected void doUniqueExceptions(String streamName) throws JetStreamApiException, IOException, InterruptedException {
        doAdmin(wctx -> {
            Map<String, Unex> map = new HashMap<>();
            StreamContext streamContext = wctx.nc.getStreamContext(streamName);
            StreamInfo si = streamContext.getStreamInfo();
            long currentlyAvailable = si.getStreamState().getMsgCount();
            long red = 0;
            long failsLeft = 10;
            long count = 0;
            OrderedConsumerContext occ = streamContext.createOrderedConsumer(new OrderedConsumerConfiguration());
            try (IterableConsumer it = occ.iterate()) {
                while (red < currentlyAvailable && failsLeft > 0) {
                    Message m = it.nextMessage(1000);
                    showProgressMaybe(++count, "Calculating Unique: " + streamName);
                    if (m == null) {
                        failsLeft--;
                    }
                    else {
                        ++red;
                        Unex unex = new Unex(this, m);
                        Unex mapUnex = map.get(unex.key);
                        if (mapUnex == null) {
                            map.put(unex.key, unex);
                        }
                        else {
                            mapUnex.increment(unex);
                        }
                    }
                }
                System.out.println("\n");
                System.out.println(UN_START);
                System.out.println(UN_DESC);
                System.out.println(UN_TOP_SEP);
                System.out.println(UN_HEADER);
                System.out.println(UN_SEP);
                for (Unex unex : map.values()) {
                    String time = Debug.rfcTime(unex.time);
                    String deets = unex.event.exceptionMessage;
                    //noinspection DataFlowIssue
                    if (deets.length() > UN_WIDTH) {
                        deets = deets.substring(0, EX_WIDTH - 3) + "...";
                    }
                    //noinspection DataFlowIssue
                    String ex = unex.event.exceptionClass.replace("Exception", "");
                    System.out.printf(UN_DATA, unex.event.job, ex, time, unex.count, deets);
                }
                System.out.println(UN_FOOT);
            }
            catch (Exception e) {
                exit("Unable to complete", e);
            }
        });
    }

    public static final String UN_START   = "┌───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐";
    public static final String UN_DESC    = "│ Unique Exceptions                                                                                                                             │";
    public static final String UN_TOP_SEP = "├────────────────┬────────────────┬─────────────────────┬─────────┬─────────────────────────────────────────────────────────────────────────────┤";
    public static final String UN_HEADER  = "│ Job            │ Exception      │ Last Occurrence     │ Count   │ Message                                                                     │";
    public static final String UN_SEP     = "├────────────────┼────────────────┼─────────────────────┼─────────┼─────────────────────────────────────────────────────────────────────────────┤";
    public static final String UN_FOOT    = "└────────────────┴────────────────┴─────────────────────┴─────────┴─────────────────────────────────────────────────────────────────────────────┘";
    public static final String UN_DATA    = "│ %-14s │ %-14s │ %-17s │ %,7d │ %-75s │\n";
    public static final int UN_WIDTH = 75;

    // ----------------------------------------------------------------------------------------------------
    // RUNNERS
    // ----------------------------------------------------------------------------------------------------
    protected interface WorkContextRunner {
        void run(WorkContext wctx) throws IOException, JetStreamApiException, InterruptedException;
    }

    protected void doAdmin(WorkContextRunner runner) throws IOException, JetStreamApiException, InterruptedException {
        Options ao = getAdminOptions();
        try (Connection nc = Nats.connect(ao)) {
            runner.run(new WorkContext(ao, nc));
        }
    }

    public interface Worker {
        void doWork(WorkContext wctx);
    }

    protected Runnable workerRunnable(
        String job,
        Options options,
        int tix,
        WorkState ws,
        WorkContextRunner runner)
    {
        return () -> {
            boolean connected = false;
            while (!connected) {
                Connection nc = null;
                try {
                    try {
                        nc = Nats.connect(options);
                        connected = true;
                    }
                    catch (IOException e) {
                        print(job, ws.workId, ws.elapse(), e);
                        sleep(1000);
                    }
                    if (connected) {
                        printConnect(nc, job, ws.workId, tix);
                        try {
                            runner.run(new WorkContext(options, nc, tix, ws));
                            nc.close();
                        }
                        catch (IOException | JetStreamApiException e) {
                            print(job, ws.workId, ws.elapse(), e);
                            throw new RuntimeException(e);
                        }
                    }
                }
                catch (InterruptedException e) {
                    print(job, ws.workId, ws.elapse(), e);
                }
            }
        };
    }

    protected void doWorker(String job, Worker worker) throws InterruptedException {
        startJob(job);
        Options options = allOptionsShuffled().getFirst();
        WorkState ws = new WorkState();
        Thread t = new Thread(workerRunnable(job, options, NO_TIX, ws, worker::doWork));
        t.setName(job);
        t.start();
        t.join();
    }

    protected void doWorker(String job, int threadCount, Worker worker) throws InterruptedException {
        startJob(job);
        List<Options> optionsList = allOptionsShuffled();
        List<Thread> threads = new ArrayList<>(threadCount);
        WorkState ws = new WorkState();
        int option = optionsList.size() - 1;
        for (int tix = 0; tix < threadCount; tix++) {
            if (++option == optionsList.size()) {
                option = 0;
            }
            Options options = optionsList.get(option);
            Thread t = new Thread(workerRunnable(job, options, tix, ws, worker::doWork));
            t.setName(job + " " + tix + " ");
            t.start();
            threads.add(t);
        }
        for (Thread t : threads) {
            t.join();
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // LOG HELPERS
    // ----------------------------------------------------------------------------------------------------
    protected void log(String job, WorkContext wctx, long count) {
        Event event = new Event(this, job, wctx.ws.workId, wctx.tix, null, count, wctx.elapse(), null);
        Debug.info(event.ident(), event.extras());
        publish(wctx.js, event);
    }

    protected void log(JetStream js, String job, String workId, int tix, String qualifier, long count, long elapsed) {
        Event event = new Event(this, job, workId, tix, qualifier, count, elapsed, null);
        Debug.info(event.ident(), event.extras());
        publish(js, event);
    }

    protected void logNoConsole(String job, WorkContext wctx, long count) {
        publish(wctx.js, new Event(this, job, wctx.ws.workId, wctx.tix, null, count, wctx.elapse(), null));
    }

    protected void log(String job, WorkContext wctx, Exception exception) {
        String qualifier = exception.getClass().getSimpleName().replace("Exception", "");
        Event event = new Event(this, job, wctx.ws.workId, NO_TIX, qualifier, 0, wctx.elapse(), exception);
        Debug.info(event.ident(), event.extras());
        publish(wctx.js, event);
    }

    protected void sleepThenLog(String job, long sleep, WorkContext wctx, Exception exception) {
        sleep(sleep);
        log(job, wctx, exception);
    }

    protected void print(String job, String workId, int tix, String qualifier, long count, long elapsed, String message) {
        Event event = new Event(this, job, workId, tix, qualifier, count, elapsed, null);
        Debug.info(event.ident(), event.extras(message));
    }

    protected void print(String job, WorkContext wctx, String message) {
        print(job, wctx.ws.workId, wctx.tix, null, 0, wctx.elapse(), message);
    }

    protected void print(String job, String workId, long elapsed, Exception exception) {
        Event event = new Event(this, job, workId, NO_TIX, null, -1, elapsed, exception);
        Debug.info(event.ident(), event.extras());
    }

    protected void printConnect(Connection nc, String job, String workId, int tix) {
        print(job, workId, tix, "connect", 0, 0, nc.getServerInfo().getServerId());
    }

    protected void publish(JetStream js, Event event) {
        try {
            js.publish(event.subject(), event.serialize());
        }
        catch (IOException | JetStreamApiException ee) {
            Debug.info("Event Publish Error", event.ident(), event.subject(), ee.getClass().getSimpleName(), ee.getMessage(), event.extras());
        }
    }

    protected void showProgressMaybe(long count, String lineStart) {
        if (count % progressFrequency == 0) {
            System.out.println(" " + count);
            System.out.print(lineStart);
        }
        else {
            System.out.print(DOT);
        }
    }

    protected void endProgress(long count) {
        if (count % progressFrequency != 0) { // last check because I might have already printed this count
            System.out.println(" " + count);
        }
    }

    protected void startProgressJob(String job) {
        System.out.println(workLabel);
        System.out.print(job);
    }

    protected void startJob(String job) {
        System.out.println(workLabel + " - " + job);
    }
}
