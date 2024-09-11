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
    // ### --label <workload label>
    //     --label what-is-being-done
    // ### --params <PATH_TO_JSON_FILE>
    //     --params /tmp/workload-92493-9983831-3-0913.json
    // ----------------------------------------------------------------------------------------------------
    public final String workload;
    public final String label;
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
        append(sb, "workload", workload, true);
        append(sb, "label", label, label != null);
        append(sb, "paramsFile", paramsFiles, true);
        return sb.toString().trim();
    }

    public void debug() {
        if (label != null) {
            Debug.info(COMMAND_LINE, "label", label);
        }
        Debug.info(COMMAND_LINE, "workload", workload);
        Debug.info(COMMAND_LINE, "paramsFile", paramsFiles);
    }

    // ----------------------------------------------------------------------------------------------------
    // Construction
    // ----------------------------------------------------------------------------------------------------
    public CommandLine(String[] args) {
        String _label = null;
        String _workload = null;
        List<String> _paramsFiles = new ArrayList<>();

        if (args != null && args.length > 0) {
            try {
                for (int x = 0; x < args.length; x++) {
                    String arg = args[x].trim();
                    switch (arg) {
                        case "--label":
                            _label = asString(args[++x]);
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
                            Debug.info(COMMAND_LINE, "Unknown argument: " + arg);
                            break;
                    }
                }
            }
            catch (Exception e) {
                Debug.info(COMMAND_LINE, "Exception while parsing, most likely missing an argument value.", e);
            }
        }

        workload = _workload;
        label = _label;
        paramsFiles = _paramsFiles;
    }

    private String asString(String val) {
        return val.trim();
    }
}
