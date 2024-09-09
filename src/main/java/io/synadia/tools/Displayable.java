package io.synadia.tools;

public interface Displayable {
    void clear();
    void showWait();
    void print(String s);
    void printf(String format, Object ... args);
    void println();
    void println(String s);
}
