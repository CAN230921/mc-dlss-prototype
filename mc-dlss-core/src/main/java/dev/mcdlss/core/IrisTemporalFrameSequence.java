package dev.mcdlss.core;

public final class IrisTemporalFrameSequence {
    private long generation = -1;
    private int width;
    private int height;
    private long frameIndex;

    public Frame advance(long generation, int width, int height) {
        if (generation < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Temporal frame identity is invalid");
        }
        boolean reset = this.generation != generation
                || this.width != width || this.height != height;
        if (reset) frameIndex = 0;
        this.generation = generation;
        this.width = width;
        this.height = height;
        return new Frame(++frameIndex, reset);
    }

    public record Frame(long index, boolean reset) {
    }
}
