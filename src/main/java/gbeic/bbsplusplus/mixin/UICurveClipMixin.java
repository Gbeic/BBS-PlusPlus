package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.BBSAddonsSettings;
import gbeic.bbsplusplus.client.ui.curves.UIShaderCurvePickerOverlayPanel;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.ui.film.clips.UICurveClip;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;

/**
 * 替换曲线剪辑的光影曲线选择弹窗。
 * <p>
 * 原版 {@link UICurveClip#offerCurveKeys} 使用单层列表展示所有可动画光影参数。
 * 本 Mixin 在入口处取消原弹窗，改为打开 BBS++ 的分层选择面板，让参数按 Iris 光影包菜单路径归类显示。
 * </p>
 */
@Mixin(UICurveClip.class)
public abstract class UICurveClipMixin
{
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
