package dev.mcdlss.neoforge;

import dev.mcdlss.core.FrameGenerationActivationPolicy;
import dev.mcdlss.core.UpscalerBackendMode;
import net.minecraft.client.Minecraft;

public final class FrameGenerationRuntimeState {
    private static final long STREAMLINE_TEMPORARY_APP_ID = 100721531L;
    private static final boolean REQUESTED =
            Boolean.getBoolean("mc_dlss.frameGeneration");
    private static final long APPLICATION_ID =
            Long.getLong("mc_dlss.ngxApplicationId", 0L);

    public static FrameGenerationActivationPolicy.Decision decision() {
        return decide(REQUESTED, applicationIdAvailable());
    }

    public static FrameGenerationActivationPolicy.Decision captureDecision() {
        var config = McDlssConfigManager.current();
        boolean fsr3Requested = config.enabled()
                && config.backend() == UpscalerBackendMode.FSR3
                && config.fsr3FrameGeneration();
        return decide(Boolean.getBoolean("mc_dlss.frameGenerationCapture")
                || fsr3Requested, true);
    }

    public static boolean applicationIdAvailable() {
        return APPLICATION_ID > 0 && APPLICATION_ID != STREAMLINE_TEMPORARY_APP_ID;
    }

    public static long applicationId() {
        return applicationIdAvailable() ? APPLICATION_ID : 0L;
    }

    public static String chineseStatus() {
        return switch (decision().reason()) {
            case ACTIVE -> "帧生成可运行";
            case NOT_REQUESTED -> "帧生成未开启";
            case APPLICATION_ID_REQUIRED -> "需要 NVIDIA NGX Application ID";
            case NO_WORLD -> "等待进入世界";
            case SCREEN_OPEN -> "菜单中自动关闭";
            case OVERLAY_OPEN -> "加载界面中自动关闭";
            case HUD_HIDDEN -> "HUD 隐藏时自动关闭";
        };
    }

    private static FrameGenerationActivationPolicy.Decision decide(
            boolean requested, boolean applicationIdAvailable) {
        Minecraft client = Minecraft.getInstance();
        return FrameGenerationActivationPolicy.decide(
                requested,
                applicationIdAvailable,
                client.level != null,
                client.screen != null,
                client.getOverlay() != null,
                client.options.hideGui);
    }

    private FrameGenerationRuntimeState() {
    }
}
