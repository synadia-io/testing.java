package io.synadia.tools;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class UnixLineEnds {
    static String INPUT = "C:\\nats\\failground";
    static String OUTPUT = "C:\\nats\\failground-unix";

    @SuppressWarnings({"CallToPrintStackTrace", "ThrowablePrintedToSystemOut"})
    public static void main(String[] args) throws Exception {
        FileUtil.visitDirsRecurse(INPUT, d -> {
            String out = d.getAbsolutePath().replace(INPUT, OUTPUT);
            if (!out.contains("\\.git")) {
                System.out.println(d + " --> " + out);
                File of = new File(out);
                if (!of.exists() && !of.mkdirs()) {
                    System.out.println("Couldn't create directory: " + out);
                    System.exit(-1);
                }
            }
            return true;
        });

        System.out.println("\n\n");
        FileUtil.visitFilesRecurse(INPUT, f -> {
            String out = f.getAbsolutePath().replace(INPUT, OUTPUT);
            if (!out.contains("\\.git")) {
                System.out.println(f + " --> " + out);
                try {
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        List<String> lines = Files.readAllLines(f.toPath());
                        for (String line : lines) {
                            fos.write(line.getBytes());
                            fos.write("\n".getBytes());
                        }
                        fos.flush();
                    }
                }
                catch (IOException e) {
                    System.out.println("Error writing file: '" + out + "' Ex:" + e);
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
            return true;
        });

    }
}
