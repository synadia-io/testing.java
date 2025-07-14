// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static io.synadia.chaos.ChaosUtils.report;

public class Chaos {
    private static long HEALTH_CHECK_DELAY = 5000;

    public static void main(String[] args) throws Exception {
        if (args != null && args.length > 0) {
            try {
                for (int x = 0; x < args.length; x++) {
                    String arg = args[x].trim();
                    if (arg.equals("--hcd")) {
                        String hcd = args[x + 1];
                        args[x] = "";
                        args[x + 1] = ""; // ChaosArguments args(...) ignores empty
                        HEALTH_CHECK_DELAY = Long.parseLong(hcd);
                        break;
                    }
                }
            }
            catch (RuntimeException e) {
            }
        }

        ChaosArguments arguments = new ChaosArguments().args(args);

        ChaosRunner runner = ChaosRunner.start(arguments);

        // just give the servers a little time to be ready be first connect
        Thread.sleep(1000);

        String[] urls = runner.getConnectionUrls();
        report("Connection Urls");
        for (String url : urls) {
            report(" ", url);
        }

        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        List<Connection> connections = new ArrayList<>(urls.length);
        for (int i = 0; i < arguments.servers; i++) {
            String connectionName = "Conn" + (i + 1);
            Options options = Options.builder().server(urls[i])
                .ignoreDiscoveredServers()
                .connectionListener(new ChaosConnectionListener(connectionName))
                .errorListener(new ChaosErrorListener(connectionName))
                .build();
            Connection connection = Nats.connect(options);
            connections.add(connection);
        }

        int[] ports = runner.getConnectionPorts();
        int[] monitorPorts = runner.getMonitorPorts();
        boolean hasMonitor = monitorPorts[0] > 0;

        String[] hzs = new String[ports.length];
        while (true) {
            Thread.sleep(HEALTH_CHECK_DELAY);
            if (hasMonitor) {
                boolean changed = false;
                for (int i = 0; i < monitorPorts.length; i++) {
                    String hz = readHealthz(monitorPorts[i]);
                    if (!hz.equals(hzs[i])) {
                        changed = true;
                        hzs[i] = hz;
                    }
                }
                if (changed) {
                    report("HealthZ");
                    for (int i = 0; i < monitorPorts.length; i++) {
                        int port = ports[i];
                        int mport = monitorPorts[i];
                        report(" ", port + "/" + mport, hzs[i]);
                    }
                }
            }
        }
    }

    private static String readHealthz(int port) {
        return readEndpoint(port, "healthz");
    }

    private static String readEndpoint(int port, String endpoint) {
        String sUrl = "http://localhost:" + port + "/" + endpoint;
        try {
            URL url = new URL(sUrl);
            InputStream inputStream = url.openStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            boolean first = true;
            String line;
            StringBuilder content = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false;
                }
                else {
                    content.append(System.lineSeparator());
                }
                content.append(line);
            }
            reader.close();
            return content.toString().trim();
        }
        catch (IOException e) {
            return e.getMessage();
        }
    }
}
