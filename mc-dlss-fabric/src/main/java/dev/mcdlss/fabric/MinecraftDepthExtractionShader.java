package dev.mcdlss.fabric;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class MinecraftDepthExtractionShader implements AutoCloseable {
    private int program;
    private int vertexArray;
    private int depthSamplerLocation;

    public MinecraftDepthExtractionShader() {
        int vertex = compile(GL20.GL_VERTEX_SHADER, MinecraftDepthExtractionShaderSource.vertex());
        int fragment = compile(
                GL20.GL_FRAGMENT_SHADER, MinecraftDepthExtractionShaderSource.fragment());
        try {
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL30.glBindFragDataLocation(program, 0, "outDepth");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException(
                        "Depth extraction program link failed: "
                                + GL20.glGetProgramInfoLog(program));
            }
            depthSamplerLocation = GL20.glGetUniformLocation(program, "DepthSampler");
            if (depthSamplerLocation < 0) {
                throw new IllegalStateException("DepthSampler uniform is unavailable");
            }
            vertexArray = GL30.glGenVertexArrays();
        } finally {
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
        }
    }

    public void render(int sourceDepthTexture, int targetFramebuffer, int width, int height) {
        if (sourceDepthTexture <= 0 || targetFramebuffer <= 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Depth extraction resources are invalid");
        }
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
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    @Override
    public void close() {
        if (vertexArray != 0) {
            GL30.glDeleteVertexArrays(vertexArray);
            vertexArray = 0;
        }
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Depth extraction shader compile failed: " + log);
        }
        return shader;
    }
}
