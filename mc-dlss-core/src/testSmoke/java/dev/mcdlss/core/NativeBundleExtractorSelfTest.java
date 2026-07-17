package dev.mcdlss.core;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

public final class NativeBundleExtractorSelfTest {
    public static void main(String[] args) throws Exception {
        byte[] first = "bootstrap".getBytes(StandardCharsets.UTF_8);
        byte[] second = "native".getBytes(StandardCharsets.UTF_8);
        String manifestText = """
                bundle.version=0.1.0
                files=mc_dlss_bootstrap.dll,mc_dlss_native.dll
                file.mc_dlss_bootstrap.dll.length=%d
                file.mc_dlss_bootstrap.dll.sha256=%s
                file.mc_dlss_native.dll.length=%d
                file.mc_dlss_native.dll.sha256=%s
                """.formatted(first.length, sha256(first), second.length, sha256(second));
        NativeBundleManifest manifest = NativeBundleManifest.parse(
                new ByteArrayInputStream(manifestText.getBytes(StandardCharsets.UTF_8)));
        ClassLoader resources = new ResourceClassLoader(Map.of(
                "native/windows-x86_64/mc_dlss_bootstrap.dll", first,
                "native/windows-x86_64/mc_dlss_native.dll", second));
        Path root = Files.createTempDirectory("mc-dlss-bundle-test");
        Path extracted = NativeBundleExtractor.extract(resources, root, manifest);
        if (!Files.readString(extracted.resolve("mc_dlss_native.dll")).equals("native")) {
            throw new AssertionError("Native bundle was not extracted");
        }
        long timestamp = Files.getLastModifiedTime(
                extracted.resolve("mc_dlss_native.dll")).toMillis();
        Path reused = NativeBundleExtractor.extract(resources, root, manifest);
        if (!reused.equals(extracted) || Files.getLastModifiedTime(
                reused.resolve("mc_dlss_native.dll")).toMillis() != timestamp) {
            throw new AssertionError("Verified native cache was not reused");
        }
        Files.writeString(extracted.resolve("mc_dlss_native.dll"), "broken");
        NativeBundleExtractor.extract(resources, root, manifest);
        if (!Files.readString(extracted.resolve("mc_dlss_native.dll")).equals("native")) {
            throw new AssertionError("Damaged native cache was not repaired");
        }
        expectFailure("bundle.version=0.1.0\nfiles=../escape.dll\n"
                + "file.../escape.dll.length=1\n"
                + "file.../escape.dll.sha256=" + "0".repeat(64));
        System.out.println("NativeBundleExtractorSelfTest passed");
    }

    private static void expectFailure(String text) throws Exception {
        try {
            NativeBundleManifest.parse(new ByteArrayInputStream(
                    text.getBytes(StandardCharsets.UTF_8)));
            throw new AssertionError("Unsafe native path was accepted");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final Map<String, byte[]> resources;

        private ResourceClassLoader(Map<String, byte[]> resources) {
            this.resources = resources;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            byte[] value = resources.get(name);
            return value == null ? null : new ByteArrayInputStream(value);
        }
    }

    private NativeBundleExtractorSelfTest() {
    }
}
