// Copyright 2021-2022 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.synadia;

import io.nats.client.Options;
import io.synadia.tools.Debug;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Integer.parseInt;

public class CommandLine {

    // ----------------------------------------------------------------------------------------------------
    // DRIVER COMMAND LINE
    // ----------------------------------------------------------------------------------------------------
    // ### --server <NATS_SERVER_URL>
    //     --server nats://localhost:4000,nats://localhost:4001,nats://localhost:4002
    // ### --id <UNIQUE ID>
    //     --id foo-bar-baz-12345
    // ### --workload <WORKLOAD_NAME>
    //     --workload stay-connected
    // ### --params <PATH_TO_JSON_FILE>
    //     --params /tmp/workload-92493-9983831-3-0913.json
    // ----------------------------------------------------------------------------------------------------
    public final String server;
    public final String id;
    public final String workload;
    public final List<String> paramsFiles;

    // ----------------------------------------------------------------------------------------------------
    // ToString
    // ----------------------------------------------------------------------------------------------------
    private void append(StringBuilder sb, String label, Object value, boolean test) {
        if (test) {
            sb.append("--").append(label).append(":").append(value).append(" ");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Client Test Config: ");
        append(sb, "server", server, true);
        append(sb, "id", id, id != null);
        append(sb, "workload", workload, true);
        append(sb, "paramsFile", paramsFiles, true);
        return sb.toString().trim();
    }

    public void debug() {
        Debug.info("Command Line", "server", server);
        Debug.info("Command Line", "id", id);
        Debug.info("Command Line", "workload", workload);
        Debug.info("Command Line", "paramsFile", paramsFiles);
    }

    // ----------------------------------------------------------------------------------------------------
    // Construction
    // ----------------------------------------------------------------------------------------------------
    public CommandLine(String[] args) {
        String _server = Options.DEFAULT_URL;
        String _id = null;
        String _workload = null;
        List<String> _paramsFiles = new ArrayList<>();

        if (args != null && args.length > 0) {
            try {
                for (int x = 0; x < args.length; x++) {
                    String arg = args[x].trim();
                    switch (arg) {
                        case "--server":
                            _server = asString(args[++x]);
                            break;
                        case "--id":
                            _id = asString(args[++x]);
                            break;
                        case "--workload":
                            _workload = asString(args[++x]);
                            break;
                        case "--params":
                            _paramsFiles.add(asString(args[++x]));
                            break;
                        case "":
                            break;
                        default:
                            Debug.info("CommandLine", "Unknown argument: " + arg);
                            break;
                    }
                }
            }
            catch (Exception e) {
                Debug.info("CommandLine", "Exception while parsing, most likely missing an argument value.", e);
            }
        }

        server = _server;
        id = _id;
        workload = _workload;
        paramsFiles = _paramsFiles;
    }

    private String asString(String val) {
        return val.trim();
    }

    private int asNumber(String name, String val, int upper) {
        int v = parseInt(val);
        if (upper == -2 && v < 1) {
            return Integer.MAX_VALUE;
        }
        if (upper > 0) {
            if (v > upper) {
                Debug.info("CommandLine", "Value for " + name + " cannot exceed " + upper);
            }
        }
        return v;
    }

    private int asNumber(String name, String val, int lower, int upper) {
        int v = parseInt(val);
        if (v < lower) {
            Debug.info("CommandLine", "Value for " + name + " cannot be less than " + lower);
        }
        if (v > upper) {
            Debug.info("CommandLine", "Value for " + name + " cannot exceed " + upper);
        }
        return v;
    }
}
