package dev.mcdlss.fabric;

public final class MinecraftMotionVectorShaderSource {
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
            uniform mat4 ClipToPrevClip;
            uniform vec2 RenderSize;
            uniform int Reset;
            out vec2 outMotion;

            void main() {
                outMotion = vec2(0.0);
                if (Reset != 0) {
                    return;
                }
                float depth = texelFetch(
                    DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
                if (isnan(depth) || isinf(depth) || depth < 0.0 || depth >= 0.9999) {
                    return;
                }
                vec2 currentPixel = gl_FragCoord.xy;
                vec2 currentNdc = currentPixel * 2.0 / RenderSize - 1.0;
                vec4 currentClip = vec4(currentNdc, depth * 2.0 - 1.0, 1.0);
                vec4 previousClip = ClipToPrevClip * currentClip;
                if (isnan(previousClip.w) || isinf(previousClip.w)
                        || previousClip.w <= 0.000001) {
                    return;
                }
                vec2 previousNdc = previousClip.xy / previousClip.w;
                if (any(isnan(previousNdc)) || any(isinf(previousNdc))) {
                    return;
                }
                vec2 previousPixel = (previousNdc * 0.5 + 0.5) * RenderSize;
                vec2 motion = currentPixel - previousPixel;
                outMotion = clamp(motion, -RenderSize, RenderSize);
            }
            """;

    public static String vertex() {
        return VERTEX;
    }

    public static String fragment() {
        return FRAGMENT;
    }

    private MinecraftMotionVectorShaderSource() {
    }
}
