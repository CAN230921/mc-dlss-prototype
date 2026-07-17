package dev.mcdlss.fabric;

public final class MinecraftDepthExtractionShaderSource {
    private static final String VERTEX = """
            #version 150
            void main() {
                vec2 position = vec2(
                    (gl_VertexID == 1) ? 3.0 : -1.0,
                    (gl_VertexID == 2) ? 3.0 : -1.0
                );
                gl_Position = vec4(position, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 150
            uniform sampler2D DepthSampler;
            out float outDepth;
            void main() {
                outDepth = texelFetch(
                    DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
            }
            """;

    public static String vertex() {
        return VERTEX;
    }

    public static String fragment() {
        return FRAGMENT;
    }

    private MinecraftDepthExtractionShaderSource() {
    }
}
