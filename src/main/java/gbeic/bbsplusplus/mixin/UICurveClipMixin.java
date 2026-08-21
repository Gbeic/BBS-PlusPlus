package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.client.compat.iris.SafeShaderLanguageMap;
import gbeic.bbsplusplus.client.compat.iris.ShaderCurveState;
import gbeic.bbsplusplus.client.ui.curves.UIShaderCurvePickerOverlayPanel;
import gbeic.bbsplusplus.compat.irlite.IrliteCompat;
import gbeic.bbsplusplus.compat.irlite.IrliteShaderCurveBridge;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.clips.UICurveClip;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 改造曲线剪辑的光影曲线交互。
 * <p>
 * 一是替换选择弹窗：原版 {@link UICurveClip#offerCurveKeys} 用单层列表展示所有可动画光影参数，
 * 这里改为打开 BBS++ 的分层选择面板，让参数按 Iris 光影包菜单路径归类显示。
 * 二是把时间轴上的轨道名从裸通道 ID 换成看得懂的文字。
 * </p>
 */
@Mixin(UICurveClip.class)
public abstract class UICurveClipMixin
{
    /** 有独立语言键的内置曲线通道，轨道名走语言文件而不是显示裸 ID */
    @Unique
    private static final Set<String> BBSPP$BUILT_IN_CURVES = Set.of(
        ShaderCurves.BRIGHTNESS,
        ShaderCurves.SUN_ROTATION,
        ShaderCurves.WEATHER,
        CurveClip.CHROMA_SKY_COLOR,
        ShaderCurveState.SUN_PATH_ROTATION,
        ShaderCurveState.CENTER_DEPTH
    );

    /**
     * 注入目标：{@code UICurveClip#offerCurveKeys(UIContext, List, Consumer)} 入口。
     * 注入原因：原实现只提供平铺列表，光影参数较多时不直观。
     * 修改行为：设置开启时打开按 Iris 注册菜单路径整理的 BBS++ 选择面板，并支持点击已选曲线来移除对应通道。
     */
    @Inject(method = "offerCurveKeys", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bbspp$openShaderCurvePicker(UIContext context, List<String> existing, Consumer<String> callback, CallbackInfo ci)
    {
        if (BBSAddonsSettings.shaderCurvePicker == null || !BBSAddonsSettings.shaderCurvePicker.get())
        {
            return;
        }

        UIOverlay.addOverlay(context, new UIShaderCurvePickerOverlayPanel(existing, (channelId, selected) ->
        {
            bbspp$toggleCurveChannel(callback, channelId, selected);
        }), 0.75F, 0.6F);
        ci.cancel();
    }

    /**
     * 注入目标：{@code UICurveClip#addKeyframeSheet} 里创建轨道标题用的 {@code IKey.constant} 调用。
     * 注入原因：原版把通道 ID 原样当轨道名显示，光影参数在时间轴上只能看到一串裸标识符，
     * 分不清哪条曲线对应光影设置里的哪个选项。
     * 修改行为：光影参数取 Iris 菜单里的本地化名称并附上原始 ID，BBS 内置曲线走语言文件，其余保持原样。
     */
    @Redirect(
        method = "addKeyframeSheet",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/l10n/keys/IKey;constant(Ljava/lang/String;)Lmchorse/bbs_mod/l10n/keys/IKey;"),
        remap = false
    )
    private IKey bbspp$localizeTrackTitle(String channelId)
    {
        if (channelId == null)
        {
            return IKey.constant("");
        }

        if (channelId.startsWith(CurveClip.SHADER_CURVES_PREFIX))
        {
            String option = channelId.substring(CurveClip.SHADER_CURVES_PREFIX.length());

            // IRLite 迁进 BBS 设置的参数没有 Iris 的 option.* 语言路径，用可读名 + 原始 ID
            if (IrliteCompat.isLoaded() && IrliteShaderCurveBridge.isIrLiteVariable(option))
            {
                return IKey.constant(IrliteShaderCurveBridge.displayName(option) + " (" + option + ")");
            }

            String localized = bbspp$findShaderOptionName(option);

            return IKey.constant(localized == null ? option : localized + " (" + option + ")");
        }

        if (BBSPP$BUILT_IN_CURVES.contains(channelId))
        {
            return L10n.lang("bbs.ui.camera.panels.curves." + channelId);
        }

        return IKey.constant(channelId);
    }

    /**
     * 从 Iris 光影菜单的本地化路径里取出选项的显示名。
     * <p>
     * 收集到的值形如「分类 &gt; 子分类 &gt; 选项名」，这里只取最后一段，避免轨道名过长。
     * </p>
     */
    @Unique
    private static String bbspp$findShaderOptionName(String option)
    {
        String path = SafeShaderLanguageMap.collect().get("option." + option);

        if (path == null || path.isBlank())
        {
            return null;
        }

        int index = path.lastIndexOf(" > ");

        return index == -1 ? path : path.substring(index + 3).trim();
    }

    @Unique
    private static void bbspp$toggleCurveChannel(Consumer<String> callback, String channelId, boolean selected)
    {
        UICurveClip clipEditor = bbspp$findCurveClip(callback);

        if (clipEditor == null)
        {
            if (selected && callback != null)
            {
                callback.accept(channelId);
            }

            return;
        }

        KeyframeChannel<?> channel = bbspp$findChannel(clipEditor, channelId);

        if (selected)
        {
            if (channel == null)
            {
                if (CurveClip.isColorChannelId(channelId))
                {
                    clipEditor.clip.channels.addChannel(channelId, KeyframeFactories.COLOR);
                }
                else
                {
                    clipEditor.clip.channels.addChannel(channelId, KeyframeFactories.DOUBLE);
                }
            }
        }
        else if (channel != null)
        {
            clipEditor.clip.channels.removeChannel(channel);
        }

        clipEditor.fillData();
    }

    @Unique
    private static KeyframeChannel<?> bbspp$findChannel(UICurveClip clipEditor, String channelId)
    {
        for (KeyframeChannel<?> channel : clipEditor.clip.channels.getAllKeyframeChannels())
        {
            if (channel.getId().equals(channelId))
            {
                return channel;
            }
        }

        return null;
    }

    @Unique
    private static UICurveClip bbspp$findCurveClip(Consumer<String> callback)
    {
        if (callback == null)
        {
            return null;
        }

        for (Field field : callback.getClass().getDeclaredFields())
        {
            if (!UICurveClip.class.isAssignableFrom(field.getType()))
            {
                continue;
            }

            try
            {
                field.setAccessible(true);

                return (UICurveClip) field.get(callback);
            }
            catch (Exception ignored)
            {}
        }

        return null;
    }
}
