package dev.mcdlss.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

public record NativeBundleManifest(String version, String bundleHash, List<Entry> entries) {
    public record Entry(String name, long length, String sha256) {
        public Entry {
            if (name == null || !name.matches("[A-Za-z0-9._-]+")) {
                throw new IllegalArgumentException("Unsafe native bundle file name");
            }
            if (length < 0 || sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException("Invalid native bundle file metadata");
            }
            sha256 = sha256.toLowerCase();
        }
    }

    public NativeBundleManifest {
        if (version == null || version.isBlank() || bundleHash == null
                || !bundleHash.matches("[0-9a-f]{64}") || entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("Invalid native bundle manifest");
        }
        entries = List.copyOf(entries);
    }

    public static NativeBundleManifest parse(InputStream input) throws IOException {
        if (input == null) throw new IllegalArgumentException("Native bundle manifest is missing");
        Properties properties = new Properties();
        properties.load(input);
        String version = required(properties, "bundle.version");
        String[] names = required(properties, "files").split(",", -1);
        List<Entry> entries = new ArrayList<>();
        for (String rawName : names) {
            String name = rawName.trim();
            Entry entry = new Entry(
                    name,
                    Long.parseLong(required(properties, "file." + name + ".length")),
                    required(properties, "file." + name + ".sha256"));
            if (entries.stream().anyMatch(existing -> existing.name().equals(entry.name()))) {
                throw new IllegalArgumentException("Duplicate native bundle file");
            }
            entries.add(entry);
        }
        String canonical = version + "\n" + entries.stream()
                .map(entry -> entry.name() + ":" + entry.length() + ":" + entry.sha256())
                .reduce("", (left, right) -> left + right + "\n");
        return new NativeBundleManifest(version, sha256(canonical), entries);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing native manifest property: " + key);
        }
        return value.trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
