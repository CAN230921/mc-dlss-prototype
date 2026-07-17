package dev.mcdlss.core;

import java.io.InputStream;
import java.nio.file.Path;

public final class NativeBundleBootstrap {
    private static final String ROOT = "native/windows-x86_64/";
    private static NativeBundleLoadResult result;

    public static synchronized NativeBundleLoadResult load(Path runDirectory) {
        if (result != null) return result;
        if (NativeBundlePlatform.current() != NativeBundlePlatform.WINDOWS_X86_64) {
            return result = NativeBundleLoadResult.unavailable(
                    "PLATFORM", "Only Windows x64 is supported");
        }
        try (InputStream input = NativeBundleBootstrap.class.getClassLoader()
                .getResourceAsStream(ROOT + "manifest.properties")) {
            if (input == null) {
                return result = NativeBundleLoadResult.unavailable(
                        "MANIFEST", "Embedded native manifest is unavailable");
            }
            NativeBundleManifest manifest = NativeBundleManifest.parse(input);
            Path cacheRoot = runDirectory.toAbsolutePath().normalize()
                    .resolve("mc-dlss").resolve("natives");
            Path bundle = NativeBundleExtractor.extract(
                    NativeBundleBootstrap.class.getClassLoader(), cacheRoot, manifest);
            Path bootstrap = bundle.resolve("mc_dlss_bootstrap.dll");
            Path main = bundle.resolve("mc_dlss_native.dll");
            System.load(bootstrap.toString());
            String error = nativePrepareDirectory(bundle.toString());
            if (error != null && !error.isBlank()) {
                return result = NativeBundleLoadResult.unavailable("BOOTSTRAP", error);
            }
            System.setProperty("mcDlss.packagedNativePath", main.toString());
            return result = NativeBundleLoadResult.ready("Extracted native bundle "
                    + manifest.bundleHash());
        } catch (Exception | LinkageError error) {
            return result = NativeBundleLoadResult.unavailable(
                    "EXTRACTION", error.getMessage());
        }
    }

    private static native String nativePrepareDirectory(String directory);

    private NativeBundleBootstrap() {
    }
}
