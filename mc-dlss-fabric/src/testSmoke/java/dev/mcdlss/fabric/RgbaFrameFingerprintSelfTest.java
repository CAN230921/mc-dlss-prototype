package dev.mcdlss.fabric;

public final class RgbaFrameFingerprintSelfTest {
    public static void main(String[] args) {
        matchesKnownFnvValue();
        detectsWorldLikeVariation();
        System.out.println("RgbaFrameFingerprintSelfTest passed");
    }

    private static void matchesKnownFnvValue() {
        RgbaFrameFingerprint fingerprint = RgbaFrameFingerprint.fromRgba8(
                new byte[] {0, 0, 0, (byte) 0xff});
        if (!"4d25077f9dcd5758".equals(fingerprint.hashHex())) {
            throw new AssertionError("Unexpected FNV-1a result: " + fingerprint.hashHex());
        }
        if (fingerprint.nonUniform() || fingerprint.nonBlackPixelCount() != 0) {
            throw new AssertionError("One black pixel must remain uniform and black");
        }
    }

    private static void detectsWorldLikeVariation() {
        RgbaFrameFingerprint fingerprint = RgbaFrameFingerprint.fromRgba8(
                new byte[] {
                        0, 0, 0, (byte) 0xff,
                        8, 4, 2, (byte) 0xff
                });
        if (!fingerprint.nonUniform() || fingerprint.nonBlackPixelCount() != 1) {
            throw new AssertionError("Expected one non-black pixel and image variation");
        }
    }

    private RgbaFrameFingerprintSelfTest() {
    }
}
