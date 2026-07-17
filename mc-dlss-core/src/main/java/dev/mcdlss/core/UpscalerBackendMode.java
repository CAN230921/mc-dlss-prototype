package dev.mcdlss.core;

public enum UpscalerBackendMode {
    DLSS("DLSS"),
    FSR3("FSR 3");

    private final String label;

    UpscalerBackendMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
