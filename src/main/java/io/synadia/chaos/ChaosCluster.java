// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.synadia.chaos.ChaosUtils.out;

public class ChaosCluster implements Runnable {
    private int SPECIFIC_PORT = 4222;
    private int SERVER_COUNT = 3; // 1, 3, 5
    private long DELAY = 3000; // the delay to bring a server down
    private long INITIAL_DELAY = 3000; // the delay to bring a server down the first time
    private long DOWN_TIME = 3000; // how long before bringing the server up
    private int HEALTH_CHECK_DELAY = 1000;

    private final AtomicBoolean keepGoing = new AtomicBoolean(false);

    public void stop() {
        keepGoing.set(false);
    }

    public void run() {
        ChaosArguments arguments = new ChaosArguments()
            .servers(SERVER_COUNT)
            .specificPort(SPECIFIC_PORT)
            .serverNamePrefix("cr-example-server")
            .clusterName("cr-example-cluster")
            .delay(DELAY)
            .initialDelay(INITIAL_DELAY)
            .downTime(DOWN_TIME);

        ChaosRunner runner = ChaosRunner.start(arguments);
        try {
            // just give the servers a little time to be ready be first connect
            Thread.sleep(1000);

            String[] urls = runner.getConnectionUrls();
            out("Connection Urls");
            for (String url : urls) {
                out(" ", url);
            }

            int[] ports = runner.getConnectionPorts();
            int[] monitorPorts = runner.getMonitorPorts();
            boolean hasMonitor = monitorPorts[0] > 0;

            String[] hzs = new String[ports.length];
            while (keepGoing.get()) {
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
                        out("HealthZ");
                        for (int i = 0; i < monitorPorts.length; i++) {
                            int port = ports[i];
                            int mport = monitorPorts[i];
                            out(" ", port + "/" + mport, hzs[i]);
                        }
                    }
                }
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            ChaosRunner.shutdown();
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
