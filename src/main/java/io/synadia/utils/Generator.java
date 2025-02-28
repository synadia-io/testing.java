package io.synadia.utils;

import io.nats.client.support.JsonParser;
import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;
import io.nats.jsmulti.shared.WarningException;
import io.synadia.workloads.Which;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nats.client.support.JsonValueUtils.readInteger;
import static io.nats.client.support.JsonValueUtils.readString;
import static io.synadia.utils.Commons.*;

public class Generator {
    enum Kind {SERVER, CLIENT, FAILGROUND}

    public static final String INPUT_DIR = "templates";
    public static final String SCRIPT_OUTPUT_DIR = "gen";
    public static final String PARAMS_OUTPUT_DIR = "params";
    public static final String SERVER_SETUP_OUTPUT_DIR = "gen-bin-server";
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

    public static final String MULTI_BUCKET = "<MultiBucket>";
    public static final String STATS_BUCKET = "<StatsBucket>";
    public static final String STATS_WATCH_WAIT_TIME = "<StatsWatchWaitTime>";
    public static final String PROFILE_BUCKET = "<ProfileBucket>";
    public static final String PROFILE_STREAM_NAME = "<ProfileStreamName>";
    public static final String PROFILE_STREAM_SUBJECT = "<ProfileStreamSubject>";
    public static final String PROFILE_WATCH_WAIT_TIME = "<ProfileWatchWaitTime>";
    public static final String SAVE_STREAM_NAME = "<SaveStreamName>";
    public static final String SAVE_STREAM_SUBJECT = "<SaveStreamSubject>";

    public static final String DO_NOT_MATCH = "do-not-match";

    public static void main(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            args = new String[]{"full"};
        }
        Which which = Which.instance(GENERATOR, args[0]);
        String generatorJsonVariant = args.length == 2 ? args[1] : "";

        Config cfg = new Config(generatorJsonVariant);
        if (which != Which.Show) {
            cfg.print();
            prepareOutputDirs();
        }
        Calculations calc = new Calculations(cfg);

        if (which == Which.Local) {
            calculateLocal(cfg, calc);
        }
        else {
            calculateAws(cfg, calc, which);
        }

        if (calc.clients > 0) {
            generate(START_CLIENTS_BAT, calc.startSshTemplate, SCRIPT_OUTPUT_DIR);
        }

        Kind lastKind = null;
        for (int x = 0; x < cfg.serverCount; x++) {
            String scriptName = "server" + x;

            Instance current = calc.runningServers.getFirst();
            String port = current.ports.get(x % current.ports.size());
            String privateServer = cfg.natsProto + current.privateIpAddr + ":" + port;
            String publicServer = cfg.natsProto + current.publicIpAddr + ":" + port;

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

            if (which != Which.Local && cfg.doPublic && !current.failground) {
                lastKind = printInstance(lastKind, Kind.SERVER, current, scriptName);
                printSsh(current, Kind.SERVER, cfg);
                printNatsCli(current);
                System.out.println();

                // SERVER SCRIPT
                if (which == Which.Full && calc.runningServers.size() == 3) {
                    String template = readTemplate("server.sh", cfg)
                        .replace("<InstancePrefix>", cfg.instancePrefix)
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

    private static Kind printInstance(Kind lastKind, Kind thisKind, Instance instance, String extra) {
        if (lastKind != thisKind) {
            System.out.println("\n" + thisKind);
        }
        System.out.println(instance.name
            + " [" + instance.stateName + "]"
            + (extra == null ? "" : " " + extra)
        );
        return thisKind;
    }

    private static void printNatsCli(Instance instance) {
        for (int x = 0; x < instance.ports.size(); x++) {
            System.out.println("nats s list -a -s nats://" + instance.publicIpAddr + ":" + instance.ports.get(x));
        }
    }

    private static String printSsh(Instance current, Kind kind, Config cfg) {
        if (!DO_NOT_MATCH.equals(cfg.keyFile)) {
            String user = switch (kind) {
                case SERVER -> cfg.serverUser;
                case CLIENT -> cfg.clientUser;
                case FAILGROUND -> cfg.failgroundUser;
            };
            String cmd = "ssh -oStrictHostKeyChecking=no -i "
                + cfg.keyFile + " "
                + user
                + "@" + current.publicDnsName;
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

    private static void calculateAws(Config cfg, Calculations calc, Which which) throws IOException {
        // parse the aws json
        JsonValue jv = JsonParser.parse(Files.readAllBytes(Paths.get("aws.json")));
        Kind lastKind = null;
        for (JsonValue jvRes : jv.map.get("Reservations").array) {
            for (JsonValue jvInstance : jvRes.map.get("Instances").array) {
                try {
                    Instance instance = new Instance(jvInstance, cfg.natsPorts);
                    if (instance.name.contains(cfg.clientFilter)) {
                        try {
                            lastKind = printInstance(lastKind, Kind.CLIENT, instance, null);
                            if (instance.isRunning()) {
                                String ssh = printSsh(instance, Kind.CLIENT, cfg);
                                System.out.println();
                                if (ssh != null) {
                                    String repl = SSH_PREFIX + (++calc.clients) + TAG_END;
                                    calc.startSshTemplate = calc.startSshTemplate.replace(repl, ssh);
                                }
                            }
                        }
                        catch (Exception ignore) {}
                    }
                    else if (instance.name.contains(cfg.failgroundFilter)) {
                        instance.failground = true;
                        try {
                            lastKind = printInstance(lastKind, Kind.FAILGROUND, instance, null);
                            if (instance.isRunning()) {
                                String ssh = printSsh(instance, Kind.FAILGROUND, cfg);
                                if (ssh != null) {
                                    String repl = SSH_PREFIX + (++calc.clients) + TAG_END;
                                    calc.startSshTemplate = calc.startSshTemplate.replace(repl, ssh);
                                }
                                if (which != Which.Local && cfg.doPublic) {
                                    printNatsCli(instance);
                                }
                                System.out.println();
                                calc.runningServers.add(instance);
                            }
                        }
                        catch (Exception ignore) {}
                    }
                    else if (instance.name.contains(cfg.serverFilter)) {
                        // printed somewhere else
//                        if (which == Which.Local || !cfg.doPublic) {
//                            lastKind = printInstance(lastKind, Kind.SERVER, instance, null);
//                        }
                        if (instance.isRunning()) {
                            calc.runningServers.add(instance);
                        }
                    }
                }
                catch (WarningException ignore) {}
                catch (Exception e) {
                    e.printStackTrace();
                    System.out.println(e.getMessage());
                }
            }
        }
        Collections.sort(calc.runningServers);
    }

    static class Instance implements Comparable<Instance> {
        final String name;
        final String publicDnsName;
        final String privateIpAddr;
        final String publicIpAddr;
        final Integer stateCode;
        final String stateName;
        final List<String> ports;
        boolean failground;

        @Override
        public int compareTo(Instance o) {
            return name.compareTo(o.name);
        }

        // aws
        public Instance(JsonValue jv, List<String> ports) {
            JsonValue jvTags = jv.map.get("Tags");
            if (jvTags == null) {
                throw new WarningException("Invalid Instance, Ignore");
            }
            String temp = null;
            for (JsonValue jvTag : jvTags.array) {
                String key = JsonValueUtils.readString(jvTag, "Key");
                if ("Name".equals(key)) {
                    temp = JsonValueUtils.readString(jvTag, "Value");
                }
            }
            if (temp == null) {
                throw new WarningException("Invalid Instance, Ignore");
            }
            this.name = temp;
            this.publicDnsName = JsonValueUtils.readString(jv, "PublicDnsName", "Undefined");
            this.privateIpAddr = JsonValueUtils.readString(jv, "PrivateIpAddress", "Undefined");
            this.publicIpAddr = JsonValueUtils.readString(jv, "PublicIpAddress", "Undefined");
            this.ports = ports;

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
            this.ports = new ArrayList<>();
            this.ports.add(port);
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
        public final String failgroundUser;
        public final String instancePrefix;
        public final String serverFilter;
        public final String clientFilter;
        public final String failgroundFilter;
        public final String natsProto;
        public final List<String> natsPorts;
        public final List<String> localPorts;
        public final String multiBucket;
        public final String statsBucket;
        public final String profileBucket;
        public final String profileStreamName;
        public final String profileStreamSubject;
        public final String saveStreamName;
        public final String saveStreamSubject;
        public final String statsWatchWaitTime;
        public final String profileWatchWaitTime;

        public Config(String generatorJsonVariant) throws IOException {
            JsonValue jv = loadConfig(generatorJsonVariant);
            doPublic = jv.map.get("do_public") == null || jv.map.get("do_public").bool;
            os = readString(jv, "os", OS_UNIX).equals(OS_WIN) ? OS_WIN : OS_UNIX;
            serverCount = readInteger(jv, "server_count", 3);
            windows = os.equals(OS_WIN);
            unix = !windows;
            String temp = readString(jv, ("shell_ext"));
            shellExt = temp == null ? "" : temp;
            keyFile = readString(jv, ("key_file"));
            serverUser = readString(jv, ("server_user"), DO_NOT_MATCH);
            clientUser = readString(jv, ("client_user"), DO_NOT_MATCH);
            failgroundUser = readString(jv, ("failground_user"), DO_NOT_MATCH);
            instancePrefix = readString(jv, ("instance_prefix"));
            serverFilter = readString(jv, ("server_filter"), DO_NOT_MATCH);
            clientFilter = readString(jv, ("client_filter"), DO_NOT_MATCH);
            failgroundFilter = readString(jv, ("failground_filter"), DO_NOT_MATCH);
            natsProto = readString(jv, "nats_proto", "nats://");
            natsPorts = JsonValueUtils.readStringList(jv, "nats_ports");
            localPorts = JsonValueUtils.readStringList(jv, "local_ports");

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
            return template
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
            printMaybe("doPublic", doPublic);
            printMaybe("os", os);
            printMaybe("shellExt", shellExt);
            printMaybe("keyFile", keyFile);
            printMaybe("serverUser", serverUser);
            printMaybe("clientUser", clientUser);
            printMaybe("failgroundUser", failgroundUser);
            printMaybe("instancePrefix", instancePrefix);
            printMaybe("serverFilter", serverFilter);
            printMaybe("clientFilter", clientFilter);
            printMaybe("failgroundFilter", failgroundFilter);
            printMaybe("natsProto", natsProto);
            printMaybe("natsPort", natsPorts);
            printMaybe("localPorts", localPorts);
            printMaybe("multiBucket", multiBucket);
            printMaybe("statsBucket", statsBucket);
            printMaybe("profileBucket", profileBucket);
            printMaybe("profileStreamName", profileStreamName);
            printMaybe("profileStreamSubject", profileStreamSubject);
            printMaybe("saveStreamName", saveStreamName);
            printMaybe("saveStreamSubject", saveStreamSubject);
            printMaybe("statsWatchWaitTime", statsWatchWaitTime);
            printMaybe("profileWatchWaitTime", profileWatchWaitTime);
        }

        private void printMaybe(String label, Object value) {
            if (value != null) {
                String s = value.toString();
                if (!s.isEmpty() && !s.equals(DO_NOT_MATCH)) {
                    System.out.println(label + ": " + value);
                }
            }
        }

        private JsonValue loadConfig(String generatorJsonVariant) throws IOException {
            // set the defaults
            JsonValue jv = JsonValueUtils.mapBuilder()
                .put("do_public", false)
                .put("os", "unix")
                // .put("shell_ext", ".sh")
                .put("key_file", DO_NOT_MATCH)
                .put("server_user", "ubuntu")
                .put("client_user", "ec2-user")
                .put("failground_user", "ec2-user")
                .put("instance_prefix", DO_NOT_MATCH)
                .put("server_filter", DO_NOT_MATCH)
                .put("client_filter", DO_NOT_MATCH)
                .put("failground_filter", DO_NOT_MATCH)
                .put("nats_proto", "nats://")
                .put("nats_ports", JsonValueUtils.arrayBuilder().add("4222"))
                .put("local_ports", JsonValueUtils.arrayBuilder().add("4222").add("5222").add("6222"))
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
            Path p = Paths.get("generator" + generatorJsonVariant + ".json");
            if (p.toFile().exists()) {
                JsonValue jvCustom = JsonParser.parse(Files.readAllBytes(p));
                jv.map.putAll(jvCustom.map);
            }
            return jv;
        }
    }
}
