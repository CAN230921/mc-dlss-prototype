package dev.mcdlss.fabric;

public final class MinecraftDepthExtractionShaderSourceSelfTest {
    public static void main(String[] args) {
        String vertex = MinecraftDepthExtractionShaderSource.vertex();
        String fragment = MinecraftDepthExtractionShaderSource.fragment();
        if (!vertex.contains("#version 150") || !vertex.contains("gl_VertexID")
                || !fragment.contains("uniform sampler2D DepthSampler")
                || !fragment.contains("texelFetch")
                || !fragment.contains("ivec2(gl_FragCoord.xy)")
                || !fragment.contains(".r")
                || fragment.contains("texture(")) {
            throw new AssertionError("Depth extraction shader contract mismatch");
        }
        System.out.println("MinecraftDepthExtractionShaderSourceSelfTest passed");
    }

    private MinecraftDepthExtractionShaderSourceSelfTest() {
    }
}
