package dev.mcdlss.fabric;

import net.minecraft.client.render.RenderTickCounter;

public final class LiveDlssRenderWorldHooks {
    public interface Handler {
        void beforeWorldRender(RenderTickCounter tickCounter);

        void afterWorldRender();

        default boolean redirected() {
            return false;
        }
    }

    private static final Handler NO_OP = new Handler() {
        @Override
        public void beforeWorldRender(RenderTickCounter tickCounter) {
        }

        @Override
        public void afterWorldRender() {
        }
    };

    private static volatile Handler handler = NO_OP;

    public static void install(Handler newHandler) {
        handler = newHandler == null ? NO_OP : newHandler;
    }

    public static void beforeWorldRender(RenderTickCounter tickCounter) {
        handler.beforeWorldRender(tickCounter);
    }

    public static void afterWorldRender() {
        handler.afterWorldRender();
    }

    public static boolean redirected() {
        return handler.redirected();
    }

    private LiveDlssRenderWorldHooks() {
    }
}
