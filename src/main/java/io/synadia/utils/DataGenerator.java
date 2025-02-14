package io.synadia.utils;

import io.nats.jsmulti.shared.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ThreadLocalRandom;

public class DataGenerator
{
    private static final String TEXT_SEED_FILE = "src/main/resources/text-seed.txt";

    public static void main(String[] args) throws IOException {
        generateTextFile("text-1mb.txt", "1m");
        generateBinaryFile("text-1gb.data", 1024*1024*1024);
    }

    public static void generateTextFile(String output, Object size) throws IOException {
        generateTextFile(new File(output), size);
    }

    public static void generateTextFile(File output, Object size) throws IOException {
        byte[] textBytes = Files.readAllBytes(new File(TEXT_SEED_FILE).toPath());
        try (FileOutputStream out = new FileOutputStream(output)) {
            int tbPtr = -1024;
            long left = Utils.parseLong(size.toString());
            while (left >= 1024) {
                tbPtr += 1024;
                if (tbPtr + 1023 > textBytes.length) {
                    tbPtr = 0;
                }
                out.write(textBytes, tbPtr, 1024);
                left -= 1024;
            }
            if (left > 0) {
                out.write(textBytes, 0, (int)left);
            }
        }
    }

    public static void generateBinaryFile(String output, Object size) throws IOException {
        generateBinaryFile(new File(output), size);
    }

    public static void generateBinaryFile(File output, Object size) throws IOException {
        try (FileOutputStream out = new FileOutputStream(output)) {
            long left = Utils.parseLong(size.toString());
            byte[] buf = new byte[1024];
            while (left >= 1024) {
                ThreadLocalRandom.current().nextBytes(buf);
                out.write(buf);
                left -= 1024;
            }
            if (left > 0) {
                ThreadLocalRandom.current().nextBytes(buf);
                out.write(buf, 0, (int) left);
            }
        }
    }
}
