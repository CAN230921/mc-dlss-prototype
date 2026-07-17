package dev.mcdlss.neoforge;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class IrisDepthDownsampleShader implements AutoCloseable {
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
            uniform vec2 SourceSize;
            out float outDepth;
            void main() {
                vec2 uv = gl_FragCoord.xy / SourceSize;
                outDepth = texture(DepthSampler, uv).r;
            }
            """;

    private int program;
    private int vertexArray;
    private int depthSamplerLocation;
    private int sourceSizeLocation;

    public IrisDepthDownsampleShader() {
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        try {
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL30.glBindFragDataLocation(program, 0, "outDepth");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("Iris depth program link failed: "
                        + GL20.glGetProgramInfoLog(program));
            }
            depthSamplerLocation = requireUniform("DepthSampler");
            sourceSizeLocation = requireUniform("SourceSize");
            vertexArray = GL30.glGenVertexArrays();
        } finally {
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
        }
    }

    public void render(
            int sourceDepthTexture, int targetFramebuffer,
            int sourceWidth, int sourceHeight, int width, int height) {
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
        GL20.glUniform2f(sourceSizeLocation, sourceWidth, sourceHeight);
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
            throw new IllegalStateException("Iris depth shader compile failed: " + log);
        }
        return shader;
    }
}
