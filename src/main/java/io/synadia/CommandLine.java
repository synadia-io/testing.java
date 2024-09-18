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

import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;
import io.synadia.utils.Debug;

import java.util.ArrayList;
import java.util.List;

public class CommandLine {

    private static final String COMMAND_LINE = "Command Line";

    // ----------------------------------------------------------------------------------------------------
    // DRIVER COMMAND LINE
    // ----------------------------------------------------------------------------------------------------
    // ### --workload <WORKLOAD_NAME>
    //     --workload stay-connected
    // ### --action <workload action>
    //     --action blah
    // ### --params <PATH_TO_JSON_FILE>
    //     --params /tmp/workload-92493-9983831-3-0913.json
    // ### --arg <command line arg>
    //     --arg foo
    // ----------------------------------------------------------------------------------------------------
    public final String workload;
    public final String action;
    public final List<String> paramsFiles;
    public final List<String> args;

    // ----------------------------------------------------------------------------------------------------
    // ToString
    // ----------------------------------------------------------------------------------------------------
    private void appendToString(StringBuilder sb, String name, Object value, boolean test) {
        if (test) {
            sb.append("--").append(name).append(":").append(value).append(" ");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Client Test Config: ");
        appendToString(sb, "workload", workload, true);
        appendToString(sb, "action", action, action != null);
        appendToString(sb, "paramsFile", paramsFiles, true);
        appendToString(sb, "args", args, !args.isEmpty());
        return sb.toString().trim();
    }

    private void appendToJv(JsonValueUtils.MapBuilder builder, String name, Object value, boolean test) {
        if (test) {
            builder.put(name, JsonValueUtils.toJsonValue(value));
        }
    }

    public JsonValue toJsonValue() {
        JsonValueUtils.ArrayBuilder ab = JsonValueUtils.arrayBuilder();
        for (String pf : paramsFiles) {
            ab.add(pf);
        }
        JsonValueUtils.MapBuilder b = JsonValueUtils.mapBuilder();
        appendToJv(b, "workload", workload, true);
        appendToJv(b, "action", action, action != null);
        appendToJv(b, "paramsFile", ab, true);
        appendToJv(b, "args", args, !args.isEmpty());
        return b.toJsonValue();
    }

    public void debug() {
        Debug.info(COMMAND_LINE, "workload", workload);
        if (action != null) {
            Debug.info(COMMAND_LINE, "action", action);
        }
        Debug.info(COMMAND_LINE, "paramsFile", paramsFiles);
        if (!args.isEmpty()) {
            Debug.info(COMMAND_LINE, "args", args);
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Construction
    // ----------------------------------------------------------------------------------------------------
    public CommandLine(String[] args) {
        String _workload = null;
        String _action = null;
        List<String> _paramsFiles = new ArrayList<>();
        List<String> _args = new ArrayList<>();

        if (args != null && args.length > 0) {
            try {
                for (int x = 0; x < args.length; x++) {
                    String arg = args[x].trim();
                    switch (arg) {
                        case "--action":
                            _action = asString(args[++x]);
                            break;
                        case "--workload":
                            _workload = asString(args[++x]);
                            break;
                        case "--params":
                            _paramsFiles.add(asString(args[++x]));
                            break;
                        case "--arg":
                            _args.add(asString(args[++x]));
                            break;
                        case "":
                            break;
                        default:
                            Debug.info(COMMAND_LINE, "Unknown argument: " + arg);
                            break;
                    }
                }
            }
            catch (Exception e) {
                Debug.info(COMMAND_LINE, "Exception while parsing, most likely missing an argument value.", e);
            }
        }

        this.workload = _workload;
        this.action = _action;
        this.paramsFiles = _paramsFiles;
        this.args = _args;
    }

    private String asString(String val) {
        return val.trim();
    }
}
