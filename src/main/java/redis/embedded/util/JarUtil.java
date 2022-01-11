package redis.embedded.util;

import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class JarUtil {

    public static File extractExecutableFromJar(String executable) throws IOException {
        File command = extractFileFromJar(executable);
        command.setExecutable(true);

        return command;
    }

    public static File extractFileFromJar(String path) throws IOException {
        File tmpDir = Files.createTempDir();
        tmpDir.deleteOnExit();

        File file = new File(tmpDir, path);
        FileUtils.copyURLToFile(Resources.getResource(path), file);
        file.deleteOnExit();

        return file;
    }
}
