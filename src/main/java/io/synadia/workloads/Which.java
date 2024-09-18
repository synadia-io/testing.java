package io.synadia.workloads;

import static io.synadia.utils.Constants.*;

public enum Which {

    Stats(WATCH),
    Profile(WATCH),
    Tracking(SETUP),
    Testing(SETUP),
    Save(SETUP),
    Full(GENERATOR),
    Show(GENERATOR),
    Local(GENERATOR)
    ;

    public final String group;

    Which(String group) {
        this.group = group;
    }

    public static Which instance(String group, String text) {
        for (Which w : Which.values()) {
            if (w.name().equalsIgnoreCase(text) && w.group.equals(group)) {
                return w;
            }
        }
        throw new RuntimeException("Which " + text + " not found for group " + group);
    }
}
