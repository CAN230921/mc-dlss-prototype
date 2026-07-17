package dev.mcdlss.fabric;

public final class MinecraftMotionVectorShaderSourceSelfTest {
    public static void main(String[] args) {
        String vertex = MinecraftMotionVectorShaderSource.vertex();
        String fragment = MinecraftMotionVectorShaderSource.fragment();
        for (String required : new String[] {
                "#version 150", "gl_VertexID", "uniform sampler2D DepthSampler",
                "uniform mat4 ClipToPrevClip", "uniform vec2 RenderSize",
                "uniform int Reset", "texelFetch", "depth * 2.0 - 1.0",
                "previousClip.w <= 0.000001", "currentPixel - previousPixel",
                "clamp(motion, -RenderSize, RenderSize)", "outMotion = vec2(0.0)"}) {
            if (!(vertex + fragment).contains(required)) {
                throw new AssertionError("Missing motion shader contract: " + required);
            }
        }
        if (fragment.contains("texture(")) {
            throw new AssertionError("Filtered depth sampling is forbidden");
        }
        System.out.println("MinecraftMotionVectorShaderSourceSelfTest passed");
    }

    private MinecraftMotionVectorShaderSourceSelfTest() {
    }
}
