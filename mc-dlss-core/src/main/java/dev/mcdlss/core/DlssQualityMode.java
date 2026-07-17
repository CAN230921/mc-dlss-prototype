package dev.mcdlss.core;

public enum DlssQualityMode {
    QUALITY("质量", 0),
    BALANCED("平衡", 1),
    PERFORMANCE("性能", 2),
    ULTRA_PERFORMANCE("超级性能", 3),
    DLAA("DLAA", 4);

    private final String chineseName;
    private final int nativeCode;

    DlssQualityMode(String chineseName, int nativeCode) {
        this.chineseName = chineseName;
        this.nativeCode = nativeCode;
    }

    public String chineseName() {
        return chineseName;
    }

    public int nativeCode() {
        return nativeCode;
    }
}
