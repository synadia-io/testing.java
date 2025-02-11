package io.synadia.utils;

import java.text.SimpleDateFormat;

public interface Constants {
    SimpleDateFormat FULL_DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    SimpleDateFormat FULL_DATE_FORMATTER_ALT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
    SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat("HH:mm:ss.SSSZ");

    String TIME = "time";
    String TIME_MS = "time_ms";

    String FINAL = "final";
    String OS_WIN = "win";
    String OS_UNIX = "unix";

    String WATCH = "Watch";
    String SETUP = "Setup";
    String GENERATOR = "Generator";
}
