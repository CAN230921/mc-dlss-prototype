package dev.mcdlss.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class NativeBundleExtractor {
    private static final String RESOURCE_ROOT = "native/windows-x86_64/";

    public static Path extract(
            ClassLoader classLoader, Path cacheRoot, NativeBundleManifest manifest)
            throws IOException {
        if (classLoader == null || cacheRoot == null || manifest == null) {
            throw new IllegalArgumentException("Native bundle extraction arguments are required");
        }
        Path normalizedRoot = cacheRoot.toAbsolutePath().normalize();
        Files.createDirectories(normalizedRoot);
        Path bundleDirectory = normalizedRoot.resolve(manifest.bundleHash()).normalize();
        if (!bundleDirectory.getParent().equals(normalizedRoot)) {
            throw new IllegalArgumentException("Native bundle cache escaped its root");
        }
        Path lockPath = normalizedRoot.resolve(manifest.bundleHash() + ".lock");
        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            Files.createDirectories(bundleDirectory);
            for (NativeBundleManifest.Entry entry : manifest.entries()) {
                Path target = bundleDirectory.resolve(entry.name()).normalize();
                if (!target.getParent().equals(bundleDirectory)) {
                    throw new IllegalArgumentException("Native bundle file escaped its directory");
                }
                if (verified(target, entry)) continue;
                Path temporary = Files.createTempFile(bundleDirectory, entry.name(), ".tmp");
                try (InputStream input = classLoader.getResourceAsStream(
                        RESOURCE_ROOT + entry.name())) {
                    if (input == null) {
                        throw new IOException("Embedded native file is missing: " + entry.name());
                    }
                    Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
                if (!verified(temporary, entry)) {
                    Files.deleteIfExists(temporary);
                    throw new IOException("Embedded native file failed verification: " + entry.name());
                }
                moveReplacing(temporary, target);
            }
        }
        return bundleDirectory;
    }

    private static boolean verified(Path path, NativeBundleManifest.Entry entry)
            throws IOException {
        return Files.isRegularFile(path) && Files.size(path) == entry.length()
                && sha256(path).equals(entry.sha256());
    }

    private static String sha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private NativeBundleExtractor() {
    }
}
