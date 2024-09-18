package io.synadia.utils;

import io.nats.client.support.JsonParser;
import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;
import io.synadia.workloads.Which;

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

import static io.nats.client.support.JsonValueUtils.readInteger;
import static io.nats.client.support.JsonValueUtils.readString;
import static io.synadia.utils.Constants.*;

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
    public static final String ADMIN_SERVER = "<AdminServer>";
    public static final String OS = "<OS>";
    public static final String PATH_SEP = "<PathSep>";
    public static final String ARG = "<Arg>";
    public static final String SERVER_PREFIX = "<Server";
    public static final String SSH_PREFIX = "<Ssh";
    public static final String TAG_END = ">";

    public static final String TESTING_STREAM_NAME = "<TestingStreamName>";
    public static final String TESTING_STREAM_SUBJECT = "<TestingStreamSubject>";
    public static final String MULTI_BUCKET = "<MultiBucket>";
    public static final String STATS_BUCKET = "<StatsBucket>";
    public static final String STATS_WATCH_WAIT_TIME = "<StatsWatchWaitTime>";
    public static final String PROFILE_BUCKET = "<ProfileBucket>";
    public static final String PROFILE_STREAM_NAME = "<ProfileStreamName>";
    public static final String PROFILE_STREAM_SUBJECT = "<ProfileStreamSubject>";
    public static final String PROFILE_WATCH_WAIT_TIME = "<ProfileWatchWaitTime>";
    public static final String SAVE_STREAM_NAME = "<SaveStreamName>";
    public static final String SAVE_STREAM_SUBJECT = "<SaveStreamSubject>";

    public static final String NA = "na";

    public static void main(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            args = new String[]{"full"};
        }
        Which which = Which.instance(GENERATOR, args[0]);

        Config cfg = new Config();
        if (which != Which.Show) {
            cfg.print();
            prepareOutputDirs();
        }
        Calculations calc = new Calculations(cfg);

        if (which == Which.Local) {
            calculateLocal(cfg, calc);
        }
        else {
            calculateAws(cfg, calc);
        }

        if (calc.clients > 0) {
            generate(START_CLIENTS_BAT, calc.startSshTemplate, SCRIPT_OUTPUT_DIR);
        }

        if (calc.runningServers.size() != cfg.serverCount) {
            return;
        }

        for (int x = 0; x < cfg.serverCount; x++) {
            String scriptName = "server" + x;

            Instance current = calc.runningServers.getFirst();
            String privateServer = cfg.natsProto + current.privateIpAddr + ":" + current.port;
            String publicServer = cfg.natsProto + current.publicIpAddr + ":" + current.port;

            if (x == 0) {
                calc.privateAdmin = privateServer;
                calc.publicAdmin = publicServer;
            }
            else {
                calc.privateBootstrap.append(",");
                calc.publicBootstrap.append(",");
            }
            calc.privateBootstrap.append(privateServer);
            calc.publicBootstrap.append(publicServer);

            calc.configTemplatePrivate = calc.configTemplatePrivate.replace(SERVER_PREFIX + x + TAG_END, privateServer);
            calc.configTemplatePublic = calc.configTemplatePublic.replace(SERVER_PREFIX + x + TAG_END, publicServer);

            if (which != Which.Local && cfg.doPublic) {
                heading(scriptName + " " + current.stateName);
                printSsh(current, cfg);
                printNatsCli(current);

                // SERVER SCRIPT
                if (which == Which.Full) {
                    String template = readTemplate("server.sh", cfg)
                        .replace("<InstanceId>", "" + x)
                        .replace("<PrivateIpRoute1>", calc.runningServers.get(1).privateIpAddr)
                        .replace("<PrivateIpRoute2>", calc.runningServers.get(2).privateIpAddr);
                    generate("server" + x, template, SERVER_SETUP_OUTPUT_DIR);
                }
            }

            calc.runningServers.add(calc.runningServers.removeFirst());
        }

        calc.configTemplatePrivate = finishJsonTemplatePopulate(calc.configTemplatePrivate, cfg, calc.privateBootstrap, calc.privateAdmin);
        calc.configTemplatePublic = finishJsonTemplatePopulate(calc.configTemplatePublic, cfg, calc.publicBootstrap, calc.publicAdmin);

        if (which != Which.Show) {
            File[] files = new File(INPUT_DIR).listFiles();
            if (files != null) {
                for (File f : files) {
                    String filename = f.getName();
                    if (filename.equals(CONFIG_JSON)) {
                        if (cfg.doPublic) {
                            generate(CONFIG_JSON, calc.configTemplatePublic, PARAMS_OUTPUT_DIR);
                        }
                        else {
                            generate(CONFIG_JSON, calc.configTemplatePrivate, PARAMS_OUTPUT_DIR);
                        }
                    }
                    else if (filename.endsWith(DOT_JSON)) {
                        writeJson(filename, cfg, calc.publicBootstrap, calc.publicAdmin, calc.privateBootstrap, calc.privateAdmin);
                    }
                    else if (filename.endsWith(SH_BAT_DOT_TXT)) {
                        script(filename, cfg);
                    }
                }
            }
        }
    }

    private static void writeJson(String filename, Config cfg, StringBuilder publicBootstrap, String publicAdmin, StringBuilder privateBootstrap, String privateAdmin) throws IOException {
        String jsonTemplate = readTemplate(filename, cfg);
        if (cfg.doPublic) {
            jsonTemplate = finishJsonTemplatePopulate(jsonTemplate, cfg, publicBootstrap, publicAdmin);
        }
        else {
            jsonTemplate = finishJsonTemplatePopulate(jsonTemplate, cfg, privateBootstrap, privateAdmin);
        }
        generate(filename, jsonTemplate, PARAMS_OUTPUT_DIR);
    }

    private static void script(String filename, Config cfg) throws IOException {
        String name = filename.replace(SH_BAT_DOT_TXT, "");
        String scriptTemplate = readTemplate(filename, cfg);
        String genName = cfg.unix ? name + cfg.shellExt : name + ".bat";
        generate(genName, scriptTemplate, SCRIPT_OUTPUT_DIR);
    }

    private static void heading(String label) {
        System.out.println();
        System.out.println(label);
    }

    private static void printNatsCli(Instance instance) {
        System.out.println("nats s list -a -s " + instance.publicIpAddr);
    }

    private static String printSsh(Instance current, Config cfg) {
        if (!NA.equals(cfg.keyFile)) {
            String cmd = "ssh -oStrictHostKeyChecking=no -i " + cfg.keyFile + " " + cfg.clientUser + "@" + current.publicDnsName;
            System.out.println(cmd);
            return cmd;
        }
        return null;
    }

    private static String readTemplate(String tpl, Config cfg) throws IOException {
        String template = Files.readString(Paths.get(INPUT_DIR, tpl));
        if (cfg.unix) {
            return template.replace(PATH_SEP, "/").replace(ARG, "$");
        }
        return template.replace(PATH_SEP, "\\").replace(ARG, "%");
    }

    private static String finishJsonTemplatePopulate(String template, Config cfg, StringBuilder bootstrap, String admin) {
        return cfg.populate(template.replace(BOOTSTRAP, bootstrap).replace(ADMIN_SERVER, admin).replace(OS, cfg.os));
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

    private static void calculateAws(Config cfg, Calculations calc) throws IOException {
        // parse the aws json
        JsonValue jv = JsonParser.parse(Files.readAllBytes(Paths.get("aws.json")));
        for (JsonValue jvRes : jv.map.get("Reservations").array) {
            for (JsonValue jvInstance : jvRes.map.get("Instances").array) {
                Instance instance = new Instance(jvInstance, cfg.natsPort);
                if (instance.name.contains(cfg.serverFilter)) {
                    heading("server " + instance.name + " [" + instance.stateName + "]");
                    if (instance.isRunning()) {
                        calc.runningServers.add(instance);
                    }
                }
                else if (instance.name.contains(cfg.clientFilter)) {
                    try {
                        heading("client " + instance.name + " [" + instance.stateName + "]");
                        if (instance.isRunning()) {
                            String ssh = printSsh(instance, cfg);
                            if (ssh != null) {
                                String repl = SSH_PREFIX + (++calc.clients) + TAG_END;
                                calc.startSshTemplate = calc.startSshTemplate.replace(repl, ssh);
                            }
                        }
                    }
                    catch (Exception ignore) {
                    }
                }
            }
        }
    }

    static class Instance {
        final String name;
        final String publicDnsName;
        final String privateIpAddr;
        final String publicIpAddr;
        final Integer stateCode;
        final String stateName;
        final String port;

        // aws
        public Instance(JsonValue jv, String port) {
            this.name = jv.map.get("Tags").array.getFirst().map.get("Value").string;
            this.publicDnsName = JsonValueUtils.readString(jv, "PublicDnsName", "Undefined");
            this.privateIpAddr = JsonValueUtils.readString(jv, "PrivateIpAddress", "Undefined");
            this.publicIpAddr = JsonValueUtils.readString(jv, "PublicIpAddress", "Undefined");
            this.port = port;

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

        // local
        public Instance(String port) {
            this.name = "local-" + port;
            this.publicDnsName = this.privateIpAddr = this.publicIpAddr = "localhost";
            this.port = port;
            stateCode = 0;
            stateName = "running";
        }

        boolean isRunning() {
            return stateCode != null && stateCode == 16;
        }
    }

    private static void calculateLocal(Config cfg, Calculations calc) {
        for (String lp : cfg.localPorts) {
            calc.runningServers.add(new Instance(lp));
        }
    }

    static class Calculations {
        public List<Instance> runningServers;
        public String startSshTemplate;
        public String privateAdmin;
        public String publicAdmin;
        public StringBuilder privateBootstrap;
        public StringBuilder publicBootstrap;
        public String configTemplatePrivate;
        public String configTemplatePublic;
        public int clients;

        public Calculations(Config cfg) throws IOException {
            runningServers = new ArrayList<>();
            startSshTemplate = readTemplate(START_CLIENTS_BAT_TXT, cfg);
            privateBootstrap = new StringBuilder();
            publicBootstrap = new StringBuilder();
            configTemplatePrivate = readTemplate(CONFIG_JSON, cfg);
            configTemplatePublic = configTemplatePrivate;
        }
    }

    static class Config {
        public final boolean doPublic;
        public final int serverCount;
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
        public final List<String> localPorts;
        public final String testingStreamName;
        public final String testingStreamSubject;
        public final String multiBucket;
        public final String statsBucket;
        public final String profileBucket;
        public final String profileStreamName;
        public final String profileStreamSubject;
        public final String saveStreamName;
        public final String saveStreamSubject;
        public final String statsWatchWaitTime;
        public final String profileWatchWaitTime;

        public Config() throws IOException {
            JsonValue jv = loadConfig();
            doPublic = jv.map.get("do_public") == null || jv.map.get("do_public").bool;
            os = readString(jv, "os", OS_UNIX).equals(OS_WIN) ? OS_WIN : OS_UNIX;
            serverCount = readInteger(jv, "server_count", 3);
            windows = os.equals(OS_WIN);
            unix = !windows;
            String temp = readString(jv, ("shell_ext"));
            shellExt = temp == null ? "" : temp;
            keyFile = readString(jv, ("key_file"));
            serverUser = readString(jv, ("server_user"));
            clientUser = readString(jv, ("client_user"));
            serverFilter = readString(jv, ("server_filter"));
            clientFilter = readString(jv, ("client_filter"));
            natsProto = readString(jv, ("nats_proto"));
            natsPort = readString(jv, ("nats_port"));
            localPorts = JsonValueUtils.readStringList(jv, "local_ports");

            testingStreamName = readString(jv, "testing_stream_name");
            testingStreamSubject = readString(jv, "testing_stream_subject");
            multiBucket = readString(jv, "multi_bucket");
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
                .replace(MULTI_BUCKET, multiBucket)
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

        public void print() {
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
            System.out.println("localPorts: " + localPorts);
            System.out.println("testingStreamName: " + testingStreamName);
            System.out.println("testingStreamSubject: " + testingStreamSubject);
            System.out.println("multiBucket: " + multiBucket);
            System.out.println("statsBucket: " + statsBucket);
            System.out.println("profileBucket: " + profileBucket);
            System.out.println("profileStreamName: " + profileStreamName);
            System.out.println("profileStreamSubject: " + profileStreamSubject);
            System.out.println("saveStreamName: " + saveStreamName);
            System.out.println("saveStreamSubject: " + saveStreamSubject);
            System.out.println("statsWatchWaitTime: " + statsWatchWaitTime);
            System.out.println("profileWatchWaitTime: " + profileWatchWaitTime);
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
                .put("local_ports", JsonValueUtils.arrayBuilder().add("4222").add("5222").add("6222"))
                .put("testing_stream_name", "testingStream")
                .put("testing_stream_subject", "t")
                .put("multi_bucket", "multiBucket")
                .put("stats_bucket", "statsBucket")
                .put("stats_watch_wait_time", 5000)
                .put("profile_bucket", "profileBucket")
                .put("profile_stream_name", "profileStream")
                .put("profile_stream_subject", "P.>")
                .put("profile_watch_wait_time", 5000)
                .put("save_stream_name", "saveStream")
                .put("save_stream_subject", "S.>")
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
