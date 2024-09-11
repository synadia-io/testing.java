package io.synadia.utils;

import io.nats.client.support.JsonParser;
import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nats.client.support.JsonValueUtils.readString;
import static io.synadia.utils.Constants.OS_UNIX;
import static io.synadia.utils.Constants.OS_WIN;

public class Generator {
    public static final String INPUT_DIR = "templates";
    public static final String SCRIPT_OUTPUT_DIR = "gen";
    public static final String PARAMS_OUTPUT_DIR = "params";
    public static final String SERVER_SETUP_OUTPUT_DIR = "bin-server";
    public static final String DOT_JSON = ".json";
    public static final String SH_BAT_DOT_TXT = "-sh-bat.txt";

    public static final String CONFIG_JSON = "config.json";
    public static final String START_CLIENTS_BAT = "start-clients.bat";
    public static final String START_CLIENTS_BAT_TXT = "start-clients-bat.txt";

    public static final String BOOTSTRAP = "<Bootstrap>";
    public static final String ADMIN = "<Admin>";
    public static final String OS = "<OS>";
    public static final String PATH_SEP = "<PathSep>";
    public static final String SERVER_PREFIX = "<Server";
    public static final String SSH_PREFIX = "<Ssh";
    public static final String TAG_END = ">";

    public static final String TESTING_STREAM_NAME = "<TestingStreamName>";
    public static final String TESTING_STREAM_SUBJECT = "<TestingStreamSubject>";
    public static final String STATS_BUCKET = "<StatsBucket>";
    public static final String STATS_WATCH_WAIT_TIME = "<StatsWatchWaitTime>";
    public static final String PROFILE_BUCKET = "<ProfileBucket>";
    public static final String PROFILE_STREAM_NAME = "<ProfileStreamName>";
    public static final String PROFILE_STREAM_SUBJECT = "<ProfileStreamSubject>";
    public static final String PROFILE_WATCH_WAIT_TIME = "<ProfileWatchWaitTime>";
    public static final String SAVE_STREAM_NAME = "<SaveStreamName>";
    public static final String SAVE_STREAM_SUBJECT = "<SaveStreamSubject>";

    public static final String NA = "na";

    static class Instance {
        final String name;
        final String publicDnsName;
        final String privateIpAddr;
        final String publicIpAddr;
        final Integer stateCode;
        final String stateName;

        public Instance(JsonValue jv) {
            this.name = jv.map.get("Tags").array.getFirst().map.get("Value").string;
            this.publicDnsName = JsonValueUtils.readString(jv, "PublicDnsName", "Undefined");
            this.privateIpAddr = JsonValueUtils.readString(jv, "PrivateIpAddress", "Undefined");
            this.publicIpAddr = JsonValueUtils.readString(jv, "PublicIpAddress", "Undefined");

            Map<String, JsonValue> map = jv.map.get("State").map;
            if (map == null) {
                stateCode = null;
                stateName = "unknown";
            }
            else {
                stateCode = map.get("Code").i;
                stateName = map.get("Name").string;
            }
        }

        boolean isRunning() {
            return stateCode != null && stateCode == 16;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            System.out.println("USAGE: full | show");
            return;
        }

        boolean generate = "full".equals(args[0]);

        GeneratorConfig gc = new GeneratorConfig();
        if (generate) {
            gc.show();
            prepareOutputDirs();
        }

        List<Instance> runningServers = new ArrayList<>();

        String startSshTemplate = readTemplate(START_CLIENTS_BAT_TXT, gc);
        int client = 0;

        // parse the aws json
        JsonValue jv = JsonParser.parse(Files.readAllBytes(Paths.get("aws.json")));
        for (JsonValue jvRes : jv.map.get("Reservations").array) {
            for (JsonValue jvInstance : jvRes.map.get("Instances").array) {
                Instance instance = new Instance(jvInstance);
                if (instance.name.contains(gc.serverFilter)) {
                    heading("server " + instance.name + " [" + instance.stateName + "]");
                    if (instance.isRunning()) {
                        runningServers.add(instance);
                    }
                }
                else if (instance.name.contains(gc.clientFilter)) {
                    try {
                        heading("client " + instance.name + " [" + instance.stateName + "]");
                        if (instance.isRunning()) {
                            String ssh = printSsh(instance, gc);
                            if (ssh != null) {
                                String repl = SSH_PREFIX + (++client) + TAG_END;
                                startSshTemplate = startSshTemplate.replace(repl, ssh);
                            }
                        }
                    }
                    catch (Exception ignore) {}
                }
            }
        }

        if (client > 0) {
            generate(START_CLIENTS_BAT, startSshTemplate, SCRIPT_OUTPUT_DIR);
        }

        if (runningServers.size() != 3) {
            return;
        }

        String privateAdmin = null;
        String publicAdmin = null;
        StringBuilder privateBootstrap = new StringBuilder();
        StringBuilder publicBootstrap = new StringBuilder();
        String configTemplatePrivate = readTemplate(CONFIG_JSON, gc);
        String configTemplatePublic = configTemplatePrivate;
        for (int x = 0; x < 3; x++) {
            String scriptName = "server" + x;

            Instance current = runningServers.getFirst();
            String privateServer = gc.natsProto + current.privateIpAddr + ":" + gc.natsPort;
            String publicServer = gc.natsProto + current.publicIpAddr + ":" + gc.natsPort;

            if (x == 0) {
                privateAdmin = privateServer;
                publicAdmin = publicServer;
            }
            else {
                privateBootstrap.append(",");
                publicBootstrap.append(",");
            }
            privateBootstrap.append(privateServer);
            publicBootstrap.append(publicServer);

            configTemplatePrivate = configTemplatePrivate.replace(SERVER_PREFIX + x + TAG_END, privateServer);
            configTemplatePublic = configTemplatePublic.replace(SERVER_PREFIX + x + TAG_END, publicServer);

            if (gc.doPublic) {
                heading(scriptName + " " + current.stateName);
                printSsh(current, gc);
                printNatsCli(current);

                // SERVER SCRIPT
                if (generate) {
                    String template = readTemplate("server.sh", gc)
                        .replace("<InstanceId>", "" + x)
                        .replace("<PrivateIpRoute1>", runningServers.get(1).privateIpAddr)
                        .replace("<PrivateIpRoute2>", runningServers.get(2).privateIpAddr);
                    generate("server" + x, template, SERVER_SETUP_OUTPUT_DIR);
                }
            }

            runningServers.add(runningServers.removeFirst());
        }
        configTemplatePrivate = finishJsonTemplatePopulate(configTemplatePrivate, gc, privateBootstrap, privateAdmin);
        configTemplatePublic = finishJsonTemplatePopulate(configTemplatePublic, gc, publicBootstrap, publicAdmin);

        if (generate) {
            File[] files = new File(INPUT_DIR).listFiles();
            if (files != null) {
                for (File f : files) {
                    String filename = f.getName();
                    if (filename.equals(CONFIG_JSON)) {
                        if (gc.doPublic) {
                            generate(CONFIG_JSON, configTemplatePublic, PARAMS_OUTPUT_DIR);
                        }
                        else {
                            generate(CONFIG_JSON, configTemplatePrivate, PARAMS_OUTPUT_DIR);
                        }
                    }
                    else if (filename.endsWith(DOT_JSON)) {
                        writeJson(filename, gc, publicBootstrap, publicAdmin, privateBootstrap, privateAdmin);
                    }
                    else if (filename.endsWith(SH_BAT_DOT_TXT)) {
                        script(filename, gc);
                    }
                }
            }
        }
    }

    private static void writeJson(String filename, GeneratorConfig gc, StringBuilder publicBootstrap, String publicAdmin, StringBuilder privateBootstrap, String privateAdmin) throws IOException {
        String jsonTemplate = readTemplate(filename, gc);
        if (gc.doPublic) {
            jsonTemplate = finishJsonTemplatePopulate(jsonTemplate, gc, publicBootstrap, publicAdmin);
        }
        else {
            jsonTemplate = finishJsonTemplatePopulate(jsonTemplate, gc, privateBootstrap, privateAdmin);
        }
        generate(filename, jsonTemplate, PARAMS_OUTPUT_DIR);
    }

    private static void script(String filename, GeneratorConfig gc) throws IOException {
        String name = filename.replace(SH_BAT_DOT_TXT, "");
        String scriptTemplate = readTemplate(filename, gc);
        String genName = gc.unix ? name + gc.shellExt : name + ".bat";
        generate(genName, scriptTemplate, SCRIPT_OUTPUT_DIR);
    }

    private static void heading(String label) {
        System.out.println();
        System.out.println(label);
    }

    private static void printNatsCli(Instance instance) {
        System.out.println("nats s list -a -s " + instance.publicIpAddr);
    }

    private static String printSsh(Instance current, GeneratorConfig gc) {
        if (!NA.equals(gc.keyFile)) {
            String cmd = "ssh -oStrictHostKeyChecking=no -i " + gc.keyFile + " " + gc.clientUser + "@" + current.publicDnsName;
            System.out.println(cmd);
            return cmd;
        }
        return null;
    }

    private static String readTemplate(String tpl, GeneratorConfig gc) throws IOException {
        String template = Files.readString(Paths.get(INPUT_DIR, tpl));
        if (gc.unix) {
            return template.replace(PATH_SEP, "/");
        }
        return template.replace(PATH_SEP, "\\");
    }

    private static String finishJsonTemplatePopulate(String template, GeneratorConfig gc, StringBuilder bootstrap, String admin) {
        return gc.populate(template.replace(BOOTSTRAP, bootstrap).replace(ADMIN, admin).replace(OS, gc.os));
    }

    private static void generate(String fn, String data, String dir) throws IOException {
        FileOutputStream out = new FileOutputStream(Paths.get(dir, fn).toString());
        out.write(data.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        out.close();
    }

    private static void prepareOutputDirs() {
        prepareOutputDir(PARAMS_OUTPUT_DIR);
        prepareOutputDir(SCRIPT_OUTPUT_DIR);
        prepareOutputDir(SERVER_SETUP_OUTPUT_DIR);
    }

    private static void prepareOutputDir(String dir) {
        File fDir = new File(dir);
        if (fDir.exists()) {
            // clear directory
            File[] files = fDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
        else if (!fDir.mkdirs()) {
            System.err.println("Could not make \"" + dir + "\" directory");
            System.exit(-1);
        }
    }

    static class GeneratorConfig {
        public final boolean doPublic;
        public final String os;
        public final boolean windows;
        public final boolean unix;
        public final String shellExt;
        public final String keyFile;
        public final String serverUser;
        public final String clientUser;
        public final String serverFilter;
        public final String clientFilter;
        public final String natsProto;
        public final String natsPort;
        public final String testingStreamName;
        public final String testingStreamSubject;
        public final String statsBucket;
        public final String profileBucket;
        public final String profileStreamName;
        public final String profileStreamSubject;
        public final String saveStreamName;
        public final String saveStreamSubject;
        public final String statsWatchWaitTime;
        public final String profileWatchWaitTime;

        public GeneratorConfig() throws IOException {
            JsonValue jv = loadConfig();
            doPublic = jv.map.get("do_public") == null || jv.map.get("do_public").bool;
            os = jv.map.get("os").string.equals(OS_WIN) ? OS_WIN : OS_UNIX;
            windows = os.equals(OS_WIN);
            unix = !windows;
            String temp = jv.map.get("shell_ext").string;
            shellExt = temp == null ? "" : temp;
            keyFile = jv.map.get("key_file").string;
            serverUser = jv.map.get("server_user").string;
            clientUser = jv.map.get("client_user").string;
            serverFilter = jv.map.get("server_filter").string;
            clientFilter = jv.map.get("client_filter").string;
            natsProto = jv.map.get("nats_proto").string;
            natsPort = jv.map.get("nats_port").string;

            testingStreamName = readString(jv, "testing_stream_name");
            testingStreamSubject = readString(jv, "testing_stream_subject");
            statsBucket = readString(jv, "stats_bucket");

            profileBucket = readString(jv, "profile_bucket");
            profileStreamName = readString(jv, "profile_stream_name");
            profileStreamSubject = readString(jv, "profile_stream_subject");
            saveStreamName = readString(jv, "save_stream_name");
            saveStreamSubject = readString(jv, "save_stream_subject");

            statsWatchWaitTime = jv.map.get("stats_watch_wait_time").i.toString();
            profileWatchWaitTime = jv.map.get("profile_watch_wait_time").i.toString();
        }

        public String populate(String template) {
            return template.replace(TESTING_STREAM_NAME, testingStreamName)
                .replace(TESTING_STREAM_SUBJECT, testingStreamSubject)
                .replace(STATS_BUCKET, statsBucket)
                .replace(STATS_WATCH_WAIT_TIME, statsWatchWaitTime)
                .replace(PROFILE_BUCKET, profileBucket)
                .replace(PROFILE_STREAM_NAME, profileStreamName)
                .replace(PROFILE_STREAM_SUBJECT, profileStreamSubject)
                .replace(PROFILE_WATCH_WAIT_TIME, profileWatchWaitTime)
                .replace(SAVE_STREAM_NAME, saveStreamName)
                .replace(SAVE_STREAM_SUBJECT, saveStreamSubject)
                ;
        }

        public void show() {
            System.out.println("doPublic: " + doPublic);
            System.out.println("os: " + os);
            System.out.println("shellExt: " + shellExt);
            System.out.println("keyFile: " + keyFile);
            System.out.println("serverUser: " + serverUser);
            System.out.println("clientUser: " + clientUser);
            System.out.println("serverFilter: " + serverFilter);
            System.out.println("clientFilter: " + clientFilter);
            System.out.println("natsProto: " + natsProto);
            System.out.println("natsPort: " + natsPort);
            System.out.println("testingStreamName: " + testingStreamName);
            System.out.println("testingStreamSubject: " + testingStreamSubject);
            System.out.println("statsBucket: " + statsBucket);
            System.out.println("statsWatchWaitTime: " + statsWatchWaitTime);
            System.out.println("profileBucket: " + profileBucket);
            System.out.println("profileStreamName: " + profileStreamName);
            System.out.println("profileStreamSubject: " + profileStreamSubject);
            System.out.println("profileWatchWaitTime: " + profileWatchWaitTime);
            System.out.println("saveStreamName: " + saveStreamName);
            System.out.println("saveStreamSubject: " + saveStreamSubject);
        }

        private JsonValue loadConfig() throws IOException {
            // set the defaults
            JsonValue jv = JsonValueUtils.mapBuilder()
                .put("do_public", false)
                .put("os", "unix")
                .put("shell_ext", ".sh")
                .put("key_file", NA)
                .put("server_user", "ubuntu")
                .put("client_user", "ec2-user")
                .put("server_filter", "-server-")
                .put("client_filter", NA)
                .put("nats_proto", "nats://")
                .put("nats_port", "4222")
                .put("testing_stream_name", "testingStream")
                .put("testing_stream_subject", "t")
                .put("stats_bucket", "statsBucket")
                .put("stats_watch_wait_time", 5000)
                .put("profile_bucket", "profileBucket")
                .put("profile_stream_name", "profileStream")
                .put("profile_stream_subject", "p.>")
                .put("profile_watch_wait_time", 5000)
                .put("save_stream_name", "saveStream")
                .put("save_stream_subject", "save.>")
                .toJsonValue();

            // override with custom settings
            Path p = Paths.get("generator.json");
            if (p.toFile().exists()) {
                JsonValue jvCustom = JsonParser.parse(Files.readAllBytes(p));
                jv.map.putAll(jvCustom.map);
            }
            return jv;
        }
    }
}
