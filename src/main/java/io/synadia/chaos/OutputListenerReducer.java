// Copyright 2021 The NATS Authors
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

package io.synadia.chaos;

import java.lang.ref.WeakReference;
import java.util.Map;

public class OutputListenerReducer {
    private static long MESSAGE_DUPE_AGE = 20_000;

    public static void setMessageDupeAge(long messageDupeAge) {
        MESSAGE_DUPE_AGE = messageDupeAge;
    }

    private final String outputLabel;
    private final Map<String, WeakReference<Long>> map;

    public OutputListenerReducer(String outputLabel) {
        this.outputLabel = outputLabel;
        map = new java.util.WeakHashMap<>();
    }

    public void output(String message) {
        long now = System.currentTimeMillis();
        WeakReference<Long> timeRef = map.get(message);
        Long time = timeRef == null ? null : timeRef.get();
        long elapsed = time == null ? 0 : now - time;
        if (elapsed > MESSAGE_DUPE_AGE) {
            map.put(message, new WeakReference<>(now));
            Output.controlMessage(outputLabel, message);
        }
    }
}
