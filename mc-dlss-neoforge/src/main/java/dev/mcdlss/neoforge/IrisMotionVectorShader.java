package dev.mcdlss.neoforge;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

public final class IrisMotionVectorShader implements AutoCloseable {
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
                if (Reset != 0) return;
                vec2 uv = gl_FragCoord.xy / RenderSize;
                float depth = texture(DepthSampler, uv).r;
                if (isnan(depth) || isinf(depth) || depth < 0.0 || depth >= 0.9999) return;
                vec2 currentNdc = gl_FragCoord.xy * 2.0 / RenderSize - 1.0;
                vec4 previousClip = ClipToPrevClip
                    * vec4(currentNdc, depth * 2.0 - 1.0, 1.0);
                if (isnan(previousClip.w) || isinf(previousClip.w)
                        || previousClip.w <= 0.000001) return;
                vec2 previousNdc = previousClip.xy / previousClip.w;
                if (any(isnan(previousNdc)) || any(isinf(previousNdc))) return;
                vec2 previousPixel = (previousNdc * 0.5 + 0.5) * RenderSize;
                outMotion = clamp(gl_FragCoord.xy - previousPixel, -RenderSize, RenderSize);
            }
            """;

    private int program;
    private int vertexArray;
    private int depthSamplerLocation;
    private int clipToPrevClipLocation;
    private int renderSizeLocation;
    private int resetLocation;

    public IrisMotionVectorShader() {
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        try {
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL30.glBindFragDataLocation(program, 0, "outMotion");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("Iris motion program link failed: "
                        + GL20.glGetProgramInfoLog(program));
            }
            depthSamplerLocation = requireUniform("DepthSampler");
            clipToPrevClipLocation = requireUniform("ClipToPrevClip");
            renderSizeLocation = requireUniform("RenderSize");
            resetLocation = requireUniform("Reset");
            vertexArray = GL30.glGenVertexArrays();
        } finally {
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
        }
    }

    public void render(
            int sourceDepthTexture,
            int targetFramebuffer,
            int width,
            int height,
            float[] clipToPrevClip,
            boolean reset) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFramebuffer);
        GL11.glViewport(0, 0, width, height);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColorMask(true, true, true, true);
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vertexArray);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceDepthTexture);
        GL20.glUniform1i(depthSamplerLocation, 0);
        GL20.glUniform2f(renderSizeLocation, width, height);
        GL20.glUniform1i(resetLocation, reset ? 1 : 0);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrix = stack.mallocFloat(16);
            matrix.put(clipToPrevClip).flip();
            GL20.glUniformMatrix4fv(clipToPrevClipLocation, false, matrix);
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    @Override
    public void close() {
        if (vertexArray != 0) GL30.glDeleteVertexArrays(vertexArray);
        if (program != 0) GL20.glDeleteProgram(program);
        vertexArray = 0;
        program = 0;
    }

    private int requireUniform(String name) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException(name + " uniform is unavailable");
        return location;
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Iris motion shader compile failed: " + log);
        }
        return shader;
    }
}
