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

public class Generator {

    // aws ec2 describe-instances --output json > aws.json

    private static final int WHICH_PUBLIC = 1;
    private static final int WHICH_PRIVATE = -1;
    private static final String OS_WIN = "win";
    private static final String OS_UNIX = "unix";
    private static final String NA = "na";

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
                            printSsh("client", instance, clientUser, keyFile);
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
        String serverPrivateJson = readTemplate("servers.json");
        String serverPublicJson = serverPrivateJson;
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

            serverPrivateJson = serverPrivateJson.replace("<Server" + x + ">", privateServer);
            serverPublicJson = serverPublicJson.replace("<Server" + x + ">", publicServer);

            if (doPublic) {
                heading(scriptName + " " + current.stateName);
                printSsh(scriptName, current, serverUser, keyFile);
                printNatsCli(scriptName, current);

                // SERVER x SCRIPT
                if (generate) {
                    String template = readTemplate("server.sh")
                        .replace("<InstanceId>", "" + x)
                        .replace("<PrivateIpRoute1>", runningServers.get(1).privateIpAddr)
                        .replace("<PrivateIpRoute2>", runningServers.get(2).privateIpAddr);
                    generate("server" + x, template);
                }
            }

            runningServers.add(runningServers.removeFirst());
        }

        if (generate) {
            // SERVERS
            generate("servers-private.json", serverPrivateJson);
            if (doPublic) {
                generate("servers-public.json", serverPublicJson);
            }

            script("deploy-test", os, doPublic);

            // PUBLISH
            workload("publish", privateBootstrap, privateAdmin, publicBootstrap, publicAdmin, doPublic, os);
            workload("publish-limited", "publish", privateBootstrap, privateAdmin, publicBootstrap, publicAdmin, doPublic, os);
            workload("consume", privateBootstrap, privateAdmin, publicBootstrap, publicAdmin, doPublic, os);
            workload("setup-tracking", privateBootstrap, privateAdmin, publicBootstrap, publicAdmin, doPublic, os);

            // CLIENT SCRIPT
            generate("client-init", readTemplate("client-init.sh"));
            generate("client-work", readTemplate("client-work.sh"));
        }
    }

    private static void prepareOutputDir() {
        File gen = new File("gen");
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

    private static void script(String workName, String os, boolean doPublic) throws IOException {
        script(workName, workName, os, doPublic);
    }

    private static void script(String workName, String scriptTemplateName, String os, boolean doPublic) throws IOException {
        String scriptTemplate = readTemplate(scriptTemplateName + "-sh-bat.txt");
        writeRunners(workName, scriptTemplate.replace("<Qualifier>", "-private"), WHICH_PRIVATE, os);
        if (doPublic) {
            writeRunners(workName, scriptTemplate.replace("<Qualifier>", "-public"), WHICH_PUBLIC, os);
        }
    }

    private static void workload(String workName, StringBuilder privateBootstrap, String privateAdmin, StringBuilder publicBootstrap, String publicAdmin, boolean doPublic, String os) throws IOException {
        workload(workName, workName, privateBootstrap, privateAdmin, publicBootstrap, publicAdmin, doPublic, os);
    }

    private static void workload(String workName, String scriptTemplateName, StringBuilder privateBootstrap, String privateAdmin, StringBuilder publicBootstrap, String publicAdmin, boolean doPublic, String os) throws IOException {
        String template = readTemplate(workName + ".json");
        generate(workName + "-private.json", fillPublish(template, privateBootstrap, privateAdmin));
        if (doPublic) {
            generate(workName + "-public.json", fillPublish(template, publicBootstrap, publicAdmin));
        }
        script(workName, scriptTemplateName, os, doPublic);
    }

    private static String fillPublish(String template, StringBuilder publicBootstrap, String publicAdmin) {
        return template.replace("<Bootstrap>", publicBootstrap).replace("<Admin>", publicAdmin);
    }

    private static void heading(String label) {
        System.out.println();
        System.out.println(label);
    }

    private static void printNatsCli(String scriptName, Instance current) throws IOException {
        writeBatch(scriptName, "nats", "nats s info -s " + current.publicIpAddr);
    }

    private static void printSsh(String scriptName, Instance current, String user, String keyFile) throws IOException {
        if (!NA.equals(keyFile)) {
            writeBatch(scriptName, "ssh", "ssh -oStrictHostKeyChecking=no -i " + keyFile + " " + user + "@" + current.publicDnsName);
        }
    }

    private static void writeBatch(String name, String prefix, String cmd) throws IOException {
        System.out.println(cmd);
        generate(prefix + "-" + name + ".bat", cmd);
    }

    private static void writeRunners(String name, String template, int pubPri, String devOs) throws IOException {
        if (pubPri == WHICH_PUBLIC) {
            String genName = devOs.equals(OS_UNIX) ? name + "-public" : name + ".bat";
            generate(genName, template.replace("<Gradle>", "call gradle")
                .replace("<SEP>", "\\")
                .replace("<Location>", "public"));
        }
        if (pubPri == WHICH_PRIVATE) {
            generate(name, template.replace("<Gradle>", "gradle")
                .replace("<SEP>", "/")
                .replace("<Location>", "private"));
        }
    }

    private static String readTemplate(String tpl) throws IOException {
        return Files.readString(Paths.get("templates", tpl));
    }

    private static void generate(String fn, String data) throws IOException {
        FileOutputStream out = new FileOutputStream(Paths.get("gen", fn).toString());
        out.write(data.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        out.close();
    }
}
