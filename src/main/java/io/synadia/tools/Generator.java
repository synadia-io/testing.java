package io.synadia.tools;

import io.nats.client.support.JsonParser;
import io.nats.client.support.JsonValue;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Generator {

    // aws ec2 describe-instances --output json > aws.json
    private static final String KEY_FILE = "C:\\Users\\batman\\.ssh\\scottf-faber-dev.pem";
    private static final String SERVER_USER = "ubuntu";
    private static final String CLIENT_USER = "ec2-user";

    final String name;
    final String publicDnsName;
    final String publicIpAddress;
    final String privateIps;

    public Generator(String name, Map<String, JsonValue> map) {
        this.name = name;
        this.publicDnsName = map.get("PublicDnsName").string;
        this.publicIpAddress = map.get("PublicIpAddress").string;
        this.privateIps = map.get("PrivateIpAddress").string;
    }

    public static void main(String[] args) throws Exception {
        String serverFilters = "scottf-server-";
        String clientName = "scottf-client";
        List<Generator> gens = new ArrayList<>();

        JsonValue jv = JsonParser.parse(Files.readAllBytes(Paths.get("aws.json")));
        for (JsonValue jvRes : jv.map.get("Reservations").array) {
            for (JsonValue jvInstances : jvRes.map.get("Instances").array) {
                String name = jvInstances.map.get("Tags").array.getFirst().map.get("Value").string;
                if (name.contains(serverFilters)) {
                    gens.add(new Generator(name, jvInstances.map));
                }
                else if (name.equals(clientName)) {
                    try {
                        Generator current = new Generator(name, jvInstances.map);
                        heading("client");
                        printSsh("client", current, CLIENT_USER);
                    }
                    catch (Exception ignore) {}
                }
            }
        }

        String privateAdmin = null;
        StringBuilder privateBootstrap = new StringBuilder();
        String deployTestPrivateJson = readTemplate("deploy-test.json");
        String deployTestRemoteJson = deployTestPrivateJson;
        for (int x = 0; x < 3; x++) {
            String name = "server" + x;
            heading(name);

            Generator current = gens.getFirst();
            if (x == 0) {
                privateAdmin = current.privateIps;
            }
            else {
                privateBootstrap.append(",");
            }

            printSsh(name, current, SERVER_USER);
            printNatsCli(name, current);

            privateBootstrap.append("nats://").append(current.privateIps);
            deployTestPrivateJson = deployTestPrivateJson.replace("<Server" + x + ">", current.privateIps);
            deployTestRemoteJson = deployTestRemoteJson.replace("<Server" + x + ">", current.publicIpAddress);

            // SERVER x SCRIPT
            String template = readTemplate("server.sh")
                .replace("<InstanceId>", "" + x)
                .replace("<PrivateIpRoute1>", gens.get(1).privateIps)
                .replace("<PrivateIpRoute2>", gens.get(2).privateIps);
            generate("server" + x, template);

            gens.add(gens.removeFirst());
        }

        // DEPLOY TEST
        String template = readTemplate("deploy-test-sh-bat.txt");
        generate("deploy-test-private.json", deployTestPrivateJson);
        generate("deploy-test-remote.json", deployTestRemoteJson);
        writeRunner("deploy-test", template);

        // PUBLISH
        template = readTemplate("publish.json");
        String pj = template
            .replace("<PrivateBootstrap>", privateBootstrap)
            .replace("<PrivateAdmin>", privateAdmin);
        generate("publish.json", pj);
        writeRunner("publish", template);

        // CLIENT SCRIPT
        generate("client", readTemplate("client.sh"));
    }

    private static void heading(String label) {
        System.out.println();
        System.out.println(label);
    }

    private static void printNatsCli(String name, Generator current) throws IOException {
        writeBatch(name, "nats", "nats s info -s " + current.publicIpAddress);
    }

    private static void printSsh(String name, Generator current, String user) throws IOException {
        writeBatch(name, "ssh", "ssh -oStrictHostKeyChecking=no -i " + KEY_FILE + " " + user + "@" + current.publicDnsName);
    }

    private static void writeBatch(String name, String prefix, String cmd) throws IOException {
        System.out.println(cmd);
        generate(prefix + "-" + name + ".bat", cmd);
    }

    private static void writeRunner(String name, String template) throws IOException {
        generate(name + ".bat", template.replace("<Gradle>", "call gradle")
            .replace("<SEP>", "\\")
            .replace("<Location>", "remote"));
        generate(name, template.replace("<Gradle>", "gradle")
            .replace("<SEP>", "/")
            .replace("<Location>", "private"));
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
