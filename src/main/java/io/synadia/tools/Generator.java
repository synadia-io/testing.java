package io.synadia.tools;

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

import static io.synadia.tools.Constants.*;

public class Generator {

    // aws ec2 describe-instances --output json > aws.json

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
        // any arg on the command line meant to just do getInstances
        boolean generate = args == null || args.length == 0;

        GeneratorConfig gc = new GeneratorConfig();
        gc.show();

        if (generate) {
            prepareOutputDir();
        }

        List<Instance> runningServers = new ArrayList<>();
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
                            printSsh(instance, gc);
                        }
                    }
                    catch (Exception ignore) {}
                }
            }
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

                // SERVER x SCRIPT
                if (generate) {
                    String template = readTemplate("server.sh", gc)
                        .replace("<InstanceId>", "" + x)
                        .replace("<PrivateIpRoute1>", runningServers.get(1).privateIpAddr)
                        .replace("<PrivateIpRoute2>", runningServers.get(2).privateIpAddr);
                    generate("server" + x, template);
                }
            }

            runningServers.add(runningServers.removeFirst());
        }
        configTemplatePrivate = finishJsonTemplatePopulate(configTemplatePrivate, gc, privateBootstrap, privateAdmin);
        configTemplatePublic = finishJsonTemplatePopulate(configTemplatePublic, gc, publicBootstrap, publicAdmin);

        if (generate) {
            // CONFIG
            if (gc.doPublic) {
                generate(CONFIG_JSON, configTemplatePublic);
            }
            else {
                generate(CONFIG_JSON, configTemplatePrivate);
            }

            // WORKLOADS always have a pair of -sh-bat.txt and .json
            // ADMIN/SCRIPTS just have the -sh-bat.txt
            File[] files = new File(INPUT_DIR).listFiles();
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    if (name.endsWith(SH_BAT_DOT_TXT)) {
                        name = name.replace(SH_BAT_DOT_TXT, "");
                        File pair = new File(INPUT_DIR + File.separator + name + DOT_JSON);
                        if (pair.exists()) {
                            workload(name, gc, publicBootstrap, publicAdmin, privateBootstrap, privateAdmin);
                        }
                        else {
                            script(name, gc);
                        }
                    }
                }
            }
        }
    }

    private static void workload(String name, GeneratorConfig gc, StringBuilder publicBootstrap, String publicAdmin, StringBuilder privateBootstrap, String privateAdmin) throws IOException {
        String jsonTemplate = readTemplate(name + DOT_JSON, gc);
        if (gc.doPublic) {
            jsonTemplate = finishJsonTemplatePopulate(jsonTemplate, gc, publicBootstrap, publicAdmin);
        }
        else {
            jsonTemplate = finishJsonTemplatePopulate(jsonTemplate, gc, privateBootstrap, privateAdmin);
        }
        generate(name + DOT_JSON, jsonTemplate);
        script(name, gc);
    }

    private static void script(String name, GeneratorConfig gc) throws IOException {
        String scriptTemplate = readTemplate(name + SH_BAT_DOT_TXT, gc);
        String genName = gc.os.equals(OS_UNIX) ? name + gc.shellExt : name + ".bat";
        generate(genName, scriptTemplate);
    }

    private static void heading(String label) {
        System.out.println();
        System.out.println(label);
    }

    private static void printNatsCli(Instance instance) {
        System.out.println("nats s info -a -s " + instance.publicIpAddr);
    }

    private static void printSsh(Instance current, GeneratorConfig gc) {
        if (!NA.equals(gc.keyFile)) {
            System.out.println("ssh -oStrictHostKeyChecking=no -i " + gc.keyFile + " " + gc.clientUser + "@" + current.publicDnsName);
        }
    }

    private static String readTemplate(String tpl, GeneratorConfig gc) throws IOException {
        String template = Files.readString(Paths.get(INPUT_DIR, tpl));
        if (OS_WIN.equals(gc.os)) {
            return template.replace(PATH_SEP, "\\");
        }
        return template.replace(PATH_SEP, "/");
    }

    private static String finishJsonTemplatePopulate(String template, GeneratorConfig gc, StringBuilder bootstrap, String admin) {
        return gc.populate(template.replace(BOOTSTRAP, bootstrap).replace(ADMIN, admin));
    }

    private static void generate(String fn, String data) throws IOException {
        FileOutputStream out = new FileOutputStream(Paths.get(OUTPUT_DIR, fn).toString());
        out.write(data.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        out.close();
    }

    private static void prepareOutputDir() {
        File gen = new File(OUTPUT_DIR);
        if (gen.exists()) {
            // clear directory
            File[] files = gen.listFiles();
            if (files != null) {
                for (File f : files) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
        else if (!gen.mkdirs()) {
            System.err.println("Could not make \"gen\" directory");
            System.exit(-1);
        }
    }

    static class GeneratorConfig {
        public final boolean doPublic;
        public final String os;
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
        public final String statsWatchWaitTime;
        public final String profileWatchWaitTime;
        public final String profileStreamName;
        public final String profileStreamSubject;

        public GeneratorConfig() throws IOException {
            JsonValue jv = loadConfig();
            doPublic = jv.map.get("do_public") == null || jv.map.get("do_public").bool;
            os = jv.map.get("os").string.equals("win") ? OS_WIN : OS_UNIX;
            String temp = jv.map.get("shell_ext").string;
            shellExt = temp == null ? "" : temp;
            keyFile = jv.map.get("key_file").string;
            serverUser = jv.map.get("server_user").string;
            clientUser = jv.map.get("client_user").string;
            serverFilter = jv.map.get("server_filter").string;
            clientFilter = jv.map.get("client_filter").string;
            natsProto = jv.map.get("nats_proto").string;
            natsPort = jv.map.get("nats_port").string;
            testingStreamName = jv.map.get("testing_stream_name").string;
            testingStreamSubject = jv.map.get("testing_stream_subject").string;
            statsBucket = jv.map.get("stats_bucket").string;
            profileBucket = jv.map.get("profile_bucket").string;
            statsWatchWaitTime = jv.map.get("stats_watch_wait_time").i.toString();
            profileWatchWaitTime = jv.map.get("profile_watch_wait_time").i.toString();;
            profileStreamName = jv.map.get("profile_stream_name").string;
            profileStreamSubject = jv.map.get("profile_stream_subject").string;
        }

        public String populate(String template) {
            return template.replace(TESTING_STREAM_NAME, testingStreamName)
                .replace(TESTING_STREAM_SUBJECT, testingStreamSubject)
                .replace(STATS_BUCKET, statsBucket)
                .replace(STATS_WATCH_WAIT_TIME, statsWatchWaitTime)
                .replace(PROFILE_BUCKET, profileBucket)
                .replace(PROFILE_STREAM_NAME, profileStreamName)
                .replace(PROFILE_STREAM_NAME, profileStreamName)
                .replace(PROFILE_STREAM_SUBJECT, profileStreamSubject)
                .replace(PROFILE_WATCH_WAIT_TIME, profileWatchWaitTime)
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
                .put("testing_stream_name", "testing")
                .put("testing_stream_subject", "t")
                .put("stats_bucket", "statsBucket")
                .put("stats_watch_wait_time", 5000)
                .put("profile_bucket", "profileBucket")
                .put("profile_stream_name", "profileStream")
                .put("profile_stream_subject", "p.>")
                .put("profile_watch_wait_time", 5000)
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
