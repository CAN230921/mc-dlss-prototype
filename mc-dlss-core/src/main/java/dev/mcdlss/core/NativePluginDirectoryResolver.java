package dev.mcdlss.core;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class NativePluginDirectoryResolver {
    public static Path resolve(String packagedNativePath, String libraryPath) {
        if (packagedNativePath != null && !packagedNativePath.isBlank()) {
            Path library = Path.of(packagedNativePath).toAbsolutePath().normalize();
            if (Files.isRegularFile(library) && library.getParent() != null) {
                return library.getParent();
            }
        }
        if (libraryPath != null && !libraryPath.isBlank()) {
            for (String entry : libraryPath.split(Pattern.quote(File.pathSeparator))) {
                if (entry.isBlank()) continue;
                Path directory = Path.of(entry).toAbsolutePath().normalize();
                if (Files.isDirectory(directory)) return directory;
            }
        }
        throw new IllegalStateException("找不到 MC DLSS 原生组件目录");
    }

    private NativePluginDirectoryResolver() {
    }
}
