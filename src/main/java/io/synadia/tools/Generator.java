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

        JsonValue jv = loadGeneratorConfig();

        boolean doPublic = jv.map.get("do_public") == null || jv.map.get("do_public").bool;
        String os = jv.map.get("os").string.equals("win") ? OS_WIN : OS_UNIX;
        String shellExt = jv.map.get("shell_ext").string;
        if (shellExt == null) {
            shellExt = "";
        }
        String keyFile = jv.map.get("key_file").string;
        String serverUser = jv.map.get("server_user").string;
        String clientUser = jv.map.get("client_user").string;
        String serverFilter = jv.map.get("server_filter").string;
        String clientFilter = jv.map.get("client_filter").string;
        String natsProto = jv.map.get("nats_proto").string;
        String natsPort = jv.map.get("nats_port").string;

        System.out.println("doPublic: " + doPublic);
        System.out.println("os: " + os);
        System.out.println("keyFile: " + keyFile);
        System.out.println("serverUser: " + serverUser);
        System.out.println("clientUser: " + clientUser);
        System.out.println("serverFilter: " + serverFilter);
        System.out.println("clientFilter: " + clientFilter);
        System.out.println("natsProto: " + natsProto);
        System.out.println("natsPort: " + natsPort);

        if (generate) {
            prepareOutputDir();
        }

        List<Instance> runningServers = new ArrayList<>();
        jv = JsonParser.parse(Files.readAllBytes(Paths.get("aws.json")));
        for (JsonValue jvRes : jv.map.get("Reservations").array) {
            for (JsonValue jvInstance : jvRes.map.get("Instances").array) {
                Instance instance = new Instance(jvInstance);
                if (instance.name.contains(serverFilter)) {
                    heading("server " + instance.name + " [" + instance.stateName + "]");
                    if (instance.isRunning()) {
                        runningServers.add(instance);
                    }
                }
                else if (instance.name.contains(clientFilter)) {
                    try {
                        heading("client " + instance.name + " [" + instance.stateName + "]");
                        if (instance.isRunning()) {
                            printSsh(instance, clientUser, keyFile);
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
        String configTemplatePrivate = readTemplate(CONFIG_JSON, os);
        String configTemplatePublic = configTemplatePrivate;
        for (int x = 0; x < 3; x++) {
            String scriptName = "server" + x;

            Instance current = runningServers.getFirst();
            String privateServer = natsProto + current.privateIpAddr + ":" + natsPort;
            String publicServer = natsProto + current.publicIpAddr + ":" + natsPort;

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

            if (doPublic) {
                heading(scriptName + " " + current.stateName);
                printSsh(current, serverUser, keyFile);
                printNatsCli(current);

                // SERVER x SCRIPT
                if (generate) {
                    String template = readTemplate("server.sh", os)
                        .replace("<InstanceId>", "" + x)
                        .replace("<PrivateIpRoute1>", runningServers.get(1).privateIpAddr)
                        .replace("<PrivateIpRoute2>", runningServers.get(2).privateIpAddr);
                    generate("server" + x, template);
                }
            }

            runningServers.add(runningServers.removeFirst());
        }
        configTemplatePrivate = templateMore(configTemplatePrivate, privateBootstrap, privateAdmin);
        configTemplatePublic = templateMore(configTemplatePublic, publicBootstrap, publicAdmin);

        if (generate) {
            // CONFIG
            if (doPublic) {
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
                            workload(name, os, shellExt, doPublic, publicBootstrap, publicAdmin, privateBootstrap, privateAdmin);
                        }
                        else {
                            script(name, os, shellExt, doPublic);
                        }
                    }
                }
            }
        }
    }

    private static void workload(String name, String os, String shellExt, boolean doPublic, StringBuilder publicBootstrap, String publicAdmin, StringBuilder privateBootstrap, String privateAdmin) throws IOException {
        String jsonTemplate = readTemplate(name + DOT_JSON, os);
        if (doPublic) {
            jsonTemplate = templateMore(jsonTemplate, publicBootstrap, publicAdmin);
        }
        else {
            jsonTemplate = templateMore(jsonTemplate, privateBootstrap, privateAdmin);
        }
        generate(name + DOT_JSON, jsonTemplate);
        script(name, os, shellExt, doPublic);
    }

    private static void script(String name, String os, String shellExt, boolean doPublic) throws IOException {
        String scriptTemplate = readTemplate(name + SH_BAT_DOT_TXT, os);
        String genName = os.equals(OS_UNIX) ? name + shellExt : name + ".bat";
        generate(genName, scriptTemplate);
    }

    private static void heading(String label) {
        System.out.println();
        System.out.println(label);
    }

    private static void printNatsCli(Instance current) throws IOException {
        System.out.println("nats s info -a -s " + current.publicIpAddr);
    }

    private static void printSsh(Instance current, String user, String keyFile) throws IOException {
        if (!NA.equals(keyFile)) {
            System.out.println("ssh -oStrictHostKeyChecking=no -i " + keyFile + " " + user + "@" + current.publicDnsName);
        }
    }

    private static String readTemplate(String tpl, String os) throws IOException {
        String template = Files.readString(Paths.get(INPUT_DIR, tpl));
        if (OS_WIN.equals(os)) {
            return template.replace(PATH_SEP, "\\");
        }
        return template.replace(PATH_SEP, "/");
    }

    private static String templateMore(String template, StringBuilder bootstrap, String admin) {
        return template.replace(BOOTSTRAP, bootstrap).replace(ADMIN, admin);
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

    private static JsonValue loadGeneratorConfig() throws IOException {
        JsonValue jv;
        Path p = Paths.get("generator.json");
        if (p.toFile().exists()) {
            jv = JsonParser.parse(Files.readAllBytes(p));
        }
        else {
            jv = JsonValueUtils.mapBuilder()
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
                .toJsonValue();
        }
        return jv;
    }
}
