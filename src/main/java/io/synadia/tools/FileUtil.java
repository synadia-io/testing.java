package io.synadia.tools;

import java.io.File;
import java.util.function.Function;

public class FileUtil {

    public static final int ALL = 0;
    public static final int FILES = 1;
    public static final int DIRS = 2;

    public static File[] getFiles(File fDir) {
        File[] files = fDir == null ? null : fDir.listFiles();
        return files == null ? new File[0] : files;
    }

    public static void visitFilesRecurse(String dir, Function<File, Boolean> processor) throws Exception {
        visit(new File(dir), FILES, true, processor);
    }

    public static void visitDirsRecurse(String dir, Function<File, Boolean> processor) throws Exception {
        visit(new File(dir), DIRS, true, processor);
    }

    public static void visit(File f, int flag, boolean recurse, Function<File, Boolean> processor) throws Exception {
        if (!f.exists()) {
            throw new RuntimeException((f.isFile() ? "File " : "Directory ") + f.getAbsolutePath() + " does not exist.");
        }
        File[] files = getFiles(f);
        for (File ff : files) {
            if (flag == ALL || (flag == FILES && ff.isFile()) || (flag == DIRS && ff.isDirectory())) {
                if (!processor.apply(ff)) {
                    return;
                }
            }
            if (recurse && ff.isDirectory()) {
                visit(ff, flag, true, processor);
            }
        }
    }
}
