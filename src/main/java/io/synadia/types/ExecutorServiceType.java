package io.synadia.types;

public enum ExecutorServiceType {
    Original("original"),
    Virtual("virtual");

    private final String code;

    ExecutorServiceType(String c) {
        code = c;
    }

    @Override
    public String toString() {
        return code;
    }

    public static ExecutorServiceType get(String value, ExecutorServiceType dflt) {
        if (value != null) {
            for (ExecutorServiceType est : values()) {
                if (est.code.equalsIgnoreCase(value)) {
                    return est;
                }
            }
        }
        return dflt;
    }
}