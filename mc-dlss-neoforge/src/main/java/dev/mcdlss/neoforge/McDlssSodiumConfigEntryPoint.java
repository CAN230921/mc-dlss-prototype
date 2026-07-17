package dev.mcdlss.neoforge;

import dev.mcdlss.core.DlssQualityMode;
import dev.mcdlss.core.UpscalerBackendMode;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPointForge;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;

@ConfigEntryPointForge(McDlssNeoForgeMod.MOD_ID)
public final class McDlssSodiumConfigEntryPoint implements ConfigEntryPoint {
    private static final ResourceLocation ENABLED = id("enabled");
    private static final ResourceLocation QUALITY = id("quality_mode");
    private static final ResourceLocation BACKEND = id("upscaler_backend");
    private static final ResourceLocation FSR3_FG = id("fsr3_frame_generation");
    private static final ResourceLocation DIAGNOSTIC = id("diagnostic_validation");
    private static final ResourceLocation STATUS = id("status");

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        var enabled = builder.createBooleanOption(ENABLED)
                .setName(Component.literal("启用超分辨率"))
                .setTooltip(Component.literal("需要先启用 Iris 光影包。关闭后不会创建或提交 DLSS 会话。"))
                .setDefaultValue(false)
                .setStorageHandler(() -> { })
                .setBinding(McDlssConfigManager::setEnabled,
                        () -> McDlssConfigManager.current().enabled());

        var quality = builder.createEnumOption(QUALITY, DlssQualityMode.class)
                .setName(Component.literal("质量模式"))
                .setTooltip(Component.literal("质量越高画面越清晰；性能模式可提供更高帧率。"))
                .setDefaultValue(DlssQualityMode.QUALITY)
                .setStorageHandler(() -> { })
                .setAllowedValues(EnumSet.of(
                        DlssQualityMode.QUALITY,
                        DlssQualityMode.BALANCED,
                        DlssQualityMode.PERFORMANCE,
                        DlssQualityMode.ULTRA_PERFORMANCE))
                .setElementNameProvider(mode -> Component.literal(mode.chineseName()))
                .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED)
                .setBinding(McDlssConfigManager::setQualityMode,
                        () -> McDlssConfigManager.current().qualityMode());

        var backend = builder.createEnumOption(BACKEND, UpscalerBackendMode.class)
                .setName(Component.literal("超分辨率后端"))
                .setTooltip(Component.literal("可选择 NVIDIA DLSS 或 AMD FSR 3。"))
                .setDefaultValue(UpscalerBackendMode.DLSS)
                .setStorageHandler(() -> { })
                .setAllowedValues(EnumSet.allOf(UpscalerBackendMode.class))
                .setElementNameProvider(mode -> Component.literal(mode.label()))
                .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED)
                .setBinding(McDlssConfigManager::setBackend,
                        () -> McDlssConfigManager.current().backend());

        var frameGeneration = builder.createBooleanOption(FSR3_FG)
                .setName(Component.literal("FSR 3 帧生成（实验）"))
                .setTooltip(Component.literal("生成帧计算已接通；额外呈现调度完成前不用于正式帧率测试。"))
                .setDefaultValue(false)
                .setStorageHandler(() -> { })
                .setEnabledProvider(state -> state.readBooleanOption(ENABLED), ENABLED)
                .setBinding(McDlssConfigManager::setFsr3FrameGeneration,
                        () -> McDlssConfigManager.current().fsr3FrameGeneration());

        var status = builder.createExternalButtonOption(STATUS)
                .setName(Component.literal("运行状态：" + IrisDlssSessionController.status()))
                .setTooltip(Component.literal("实时详细状态同时显示在游戏画面左上角。"))
                .setEnabled(false)
                .setScreenConsumer(screen -> { });

        var diagnostic = builder.createBooleanOption(DIAGNOSTIC)
                .setName(Component.literal("诊断验证模式"))
                .setTooltip(Component.literal("仅用于排查问题。会启用逐帧验证读回并显著降低帧率。"))
                .setDefaultValue(false)
                .setStorageHandler(() -> { })
                .setBinding(McDlssConfigManager::setDiagnosticValidation,
                        () -> McDlssConfigManager.current().diagnosticValidation());

        var group = builder.createOptionGroup()
                .setName(Component.literal("超分辨率与帧生成"))
                .addOption(enabled)
                .addOption(backend)
                .addOption(quality)
                .addOption(frameGeneration)
                .addOption(diagnostic)
                .addOption(status);
        var page = builder.createOptionPage()
                .setName(Component.literal("DLSS / FSR 3"))
                .addOptionGroup(group);
        builder.registerModOptions(McDlssNeoForgeMod.MOD_ID)
                .setName("MC DLSS")
                .setVersion("0.1.0")
                .addPage(page);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(McDlssNeoForgeMod.MOD_ID, path);
    }
}
