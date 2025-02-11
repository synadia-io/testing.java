package io.synadia.utils;

import java.text.SimpleDateFormat;

public interface Constants {
    SimpleDateFormat FULL_DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    SimpleDateFormat FULL_DATE_NO_TZ_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

    String TIME = "time";
    String TIME_MS = "time_ms";

    String FINAL = "final";
    String OS_WIN = "win";
    String OS_UNIX = "unix";

    String WATCH = "Watch";
    String SETUP = "Setup";
    String GENERATOR = "Generator";
}
