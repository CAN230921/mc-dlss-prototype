package dev.mcdlss.core;

public record IrisDlssSessionKey(
        long generation,
        int sourceWidth,
        int sourceHeight,
        int outputWidth,
        int outputHeight) {
    public IrisDlssSessionKey {
        if (generation < 0 || sourceWidth <= 0 || sourceHeight <= 0
                || outputWidth <= 0 || outputHeight <= 0) {
            throw new IllegalArgumentException("Iris DLSS session key is invalid");
        }
    }

    public boolean requiresReopen(IrisDlssSessionKey next) {
        return !equals(next);
    }
}
