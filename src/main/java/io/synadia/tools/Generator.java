package io.synadia.tools;

import io.nats.client.support.JsonParser;
import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;

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

    final String name;
    final String publicDnsName;
    final String privateIpAddr;
    final String publicIpAddr;

    public Generator(String name, Map<String, JsonValue> map) {
        this.name = name;
        this.publicDnsName = map.get("PublicDnsName").string;
        this.privateIpAddr = map.get("PrivateIpAddress").string;
        this.publicIpAddr = map.get("PublicIpAddress").string;
    }

    public static void main(String[] args) throws Exception {
        Path p = Paths.get("generator.json");
        JsonValue jv;
        if (p.toFile().exists()) {
            jv = JsonParser.parse(Files.readAllBytes(p));
        }
        else {
            jv = JsonValueUtils.mapBuilder()
                .put("dev_os", "win")
                .put("key_file", NA)
                .put("server_user", "ubuntu")
                .put("client_user", "ec2-user")
                .put("server_filter", "scottf-server-")
                .put("client_filter", "scottf-client-")
                .toJsonValue();
        }

        String devOs = jv.map.get("dev_os").string.equals("win") ? OS_WIN : OS_UNIX;
        String keyFile = jv.map.get("key_file").string;
        String serverUser = jv.map.get("server_user").string;
        String clientUser = jv.map.get("client_user").string;
        String serverFilter = jv.map.get("server_filter").string;
        String clientFilter = jv.map.get("client_filter").string;

        System.out.println("devOs: " + devOs);
        System.out.println("keyFile: " + keyFile);
        System.out.println("serverUser: " + serverUser);
        System.out.println("clientUser: " + clientUser);
        System.out.println("serverFilter: " + serverFilter);
        System.out.println("clientFilter: " + clientFilter);

        List<Generator> gens = new ArrayList<>();

        jv = JsonParser.parse(Files.readAllBytes(Paths.get("aws.json")));
        for (JsonValue jvRes : jv.map.get("Reservations").array) {
            for (JsonValue jvInstances : jvRes.map.get("Instances").array) {
                String name = jvInstances.map.get("Tags").array.getFirst().map.get("Value").string;
                if (name.contains(serverFilter)) {
                    gens.add(new Generator(name, jvInstances.map));
                }
                else if (name.contains(clientFilter)) {
                    try {
                        Generator current = new Generator(name, jvInstances.map);
                        heading("client " + name);
                        printSsh("client", current, clientUser, keyFile);
                    }
                    catch (Exception ignore) {}
                }
            }
        }

        String privateAdmin = null;
        String publicAdmin = null;
        StringBuilder privateBootstrap = new StringBuilder();
        StringBuilder publicBootstrap = new StringBuilder();
        String deployTestPrivateJson = readTemplate("deploy-test.json");
        String deployTestPublicJson = deployTestPrivateJson;
        for (int x = 0; x < 3; x++) {
            String scriptName = "server" + x;

            Generator current = gens.getFirst();
            heading(scriptName + " " + current.name);
            if (x == 0) {
                privateAdmin = current.privateIpAddr;
                publicAdmin = current.publicIpAddr;
            }
            else {
                privateBootstrap.append(",");
                publicBootstrap.append(",");
            }
            privateBootstrap.append("nats://").append(current.privateIpAddr);
            publicBootstrap.append("nats://").append(current.publicIpAddr);

            printSsh(scriptName, current, serverUser, keyFile);
            printNatsCli(scriptName, current);

            deployTestPrivateJson = deployTestPrivateJson.replace("<Server" + x + ">", current.privateIpAddr);
            deployTestPublicJson = deployTestPublicJson.replace("<Server" + x + ">", current.publicIpAddr);

            // SERVER x SCRIPT
            String template = readTemplate("server.sh")
                .replace("<InstanceId>", "" + x)
                .replace("<PrivateIpRoute1>", gens.get(1).privateIpAddr)
                .replace("<PrivateIpRoute2>", gens.get(2).privateIpAddr);
            generate("server" + x, template);

            gens.add(gens.removeFirst());
        }

        // DEPLOY TEST
        String template = readTemplate("deploy-test-sh-bat.txt");
        generate("deploy-test-private.json", deployTestPrivateJson);
        generate("deploy-test-public.json", deployTestPublicJson);
        writeRunners("deploy-test", template, WHICH_PRIVATE, devOs);
        writeRunners("deploy-test", template, WHICH_PUBLIC, devOs);

        // PUBLISH
        template = readTemplate("publish.json");
        generate("publish-private.json", fillPublish(template, privateBootstrap, privateAdmin));
        generate("publish-public.json", fillPublish(template, publicBootstrap, publicAdmin));

        template = readTemplate("publish-limited.json");
        generate("publish-limited-private.json", fillPublish(template, privateBootstrap, privateAdmin));
        generate("publish-limited-public.json", fillPublish(template, publicBootstrap, publicAdmin));

        template = readTemplate("publish-sh-bat.txt");
        writeRunners("publish", template.replace("<Qualifier>", "-private"), WHICH_PRIVATE, devOs);
        writeRunners("publish", template.replace("<Qualifier>", "-public"), WHICH_PUBLIC, devOs);
        writeRunners("publish-limited", template.replace("<Qualifier>", "-limited-private"), WHICH_PRIVATE, devOs);
        writeRunners("publish-limited", template.replace("<Qualifier>", "-limited-public"), WHICH_PUBLIC, devOs);

        // CLIENT SCRIPT
        generate("client", readTemplate("client.sh"));
    }

    private static String fillPublish(String template, StringBuilder publicBootstrap, String publicAdmin) {
        return template.replace("<Bootstrap>", publicBootstrap).replace("<Admin>", publicAdmin);
    }

    private static void heading(String label) {
        System.out.println();
        System.out.println(label);
    }

    private static void printNatsCli(String scriptName, Generator current) throws IOException {
        writeBatch(scriptName, "nats", "nats s info -s " + current.publicIpAddr);
    }

    private static void printSsh(String scriptName, Generator current, String user, String keyFile) throws IOException {
        if (NA.equals(keyFile)) {
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
        byte[] bytes = data.getBytes(StandardCharsets.US_ASCII);
        FileOutputStream out = new FileOutputStream(Paths.get("gen", fn).toString());
        out.write(bytes);
        out.flush();
        out.close();
    }
}
