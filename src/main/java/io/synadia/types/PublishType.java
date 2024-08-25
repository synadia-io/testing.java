package io.synadia.types;

public enum PublishType {
    Sync("sync"),
    AsyncSawtooth("sawtooth");

    private final String code;

    PublishType(String c) {
        code = c;
    }

    @Override
    public String toString() {
        return code;
    }

    public static PublishType get(String value, PublishType dflt) {
        if (value != null) {
            for (PublishType est : values()) {
                if (est.code.equalsIgnoreCase(value)) {
                    return est;
                }
            }
        }
        return dflt;
    }
}