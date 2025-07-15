// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.chaos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;

public class HealthChecker implements Runnable {
    private static final long HEALTH_CHECK_DELAY = 5000;

    private final int[] ports;
    private final int[] monitorPorts;
    private final boolean hasMonitor;

    public HealthChecker(ChaosRunner runner) {
        this.ports = runner.getConnectionPorts();
        this.monitorPorts = runner.getMonitorPorts();
        this.hasMonitor = monitorPorts[0] > 0;
    }

    @Override
    public void run() {
        String[] hzs = new String[ports.length];
        while (true) {
            try {
                //noinspection BusyWait
                Thread.sleep(HEALTH_CHECK_DELAY);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
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
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < monitorPorts.length; i++) {
                        int port = ports[i];
                        int mport = monitorPorts[i];
                        if (monitorPorts.length > 1) {
                            sb.append("\n");
                        }
                        sb.append(port).append("/").append(mport).append(" ").append(hzs[i]);
                    }
                    Output.message("HEALTHZ", sb.toString());
                }
            }
        }

    }

    private static String readHealthz(int port) {
        return readEndpoint(port, "healthz");
    }

    @SuppressWarnings("SameParameterValue")
    private static String readEndpoint(int port, String endpoint) {
        String sUrl = "http://localhost:" + port + "/" + endpoint;
        try {
            URI uri = new URI(sUrl);
            InputStream inputStream = uri.toURL().openStream();
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
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
