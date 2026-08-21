package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.client.BBSPrivateClipboard;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 接管 BBS 结构化复制数据的系统剪贴板读写。
 * <p>
 * 原版 {@link Window} 会把 Map/List 形式的 BBS 元数据写入系统剪贴板。
 * 这里仅在 BBS++ 私有剪贴板开关开启时拦截这些结构化数据，让回放、
 * 关键帧、形态、变换等复制内容留在游戏内部；普通字符串复制仍走系统剪贴板。
 * </p>
 */
@Mixin(value = Window.class, remap = false)
public class WindowClipboardMixin
{
    /**
     * 注入目标：写入字符串到系统剪贴板之前。
     * 注入原因：普通文本复制代表系统剪贴板的当前内容已经变化。
     * 修改行为：不拦截字符串写入，只清空 BBS++ 私有剪贴板，避免粘贴旧的结构化数据。
     */
    @Inject(method = "setClipboard(Ljava/lang/String;)V", at = @At("HEAD"))
    private static void bbspp$clearPrivateClipboardOnStringCopy(String string, CallbackInfo ci)
    {
        if (BBSPrivateClipboard.isEnabled())
        {
            BBSPrivateClipboard.clear();
        }
    }

    /**
     * 注入目标：写入 BBS 结构化数据到系统剪贴板之前。
     * 注入原因：这类数据通常是长 JSON/元数据，不适合污染系统剪贴板。
     * 修改行为：开关开启时写入 BBS++ 私有剪贴板并取消原系统剪贴板写入。
     */
    @Inject(method = "setClipboard(Lmchorse/bbs_mod/data/types/BaseType;)V", at = @At("HEAD"), cancellable = true)
    private static void bbspp$copyDataToPrivateClipboard(BaseType data, CallbackInfo ci)
    {
        if (BBSPrivateClipboard.isEnabled() && data != null)
        {
            BBSPrivateClipboard.copy(data);
            ci.cancel();
        }
    }

    /**
     * 注入目标：写入带校验键的 BBS 结构化数据到系统剪贴板之前。
     * 注入原因：回放、关键帧、预设等复制依赖校验键识别数据类型，但不需要进入系统剪贴板。
     * 修改行为：开关开启时在副本中写入校验键，保存到私有剪贴板并取消原系统剪贴板写入。
     */
    @Inject(method = "setClipboard(Lmchorse/bbs_mod/data/types/MapType;Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private static void bbspp$copyVerifiedMapToPrivateClipboard(MapType data, String verificationKey, CallbackInfo ci)
    {
        if (BBSPrivateClipboard.isEnabled() && data != null)
        {
            BBSPrivateClipboard.copy(data, verificationKey);
            ci.cancel();
        }
    }

    /**
     * 注入目标：从系统剪贴板读取 Map 数据之前。
     * 注入原因：BBS 粘贴逻辑需要优先读取最近复制的私有结构化数据。
     * 修改行为：私有剪贴板有当前数据时直接返回其 Map 副本；否则回退到原系统剪贴板。
     */
    @Inject(method = "getClipboardMap()Lmchorse/bbs_mod/data/types/MapType;", at = @At("HEAD"), cancellable = true)
    private static void bbspp$getPrivateClipboardMap(CallbackInfoReturnable<MapType> cir)
    {
        if (BBSPrivateClipboard.isEnabled() && BBSPrivateClipboard.hasCurrentData())
        {
            cir.setReturnValue(BBSPrivateClipboard.getMap());
        }
    }

    /**
     * 注入目标：从系统剪贴板读取带校验键的 Map 数据之前。
     * 注入原因：回放、关键帧、预设等粘贴应匹配私有剪贴板中的当前 BBS 数据类型。
     * 修改行为：私有剪贴板有当前数据时按校验键返回副本或 null；否则回退到原系统剪贴板。
     */
    @Inject(method = "getClipboardMap(Ljava/lang/String;)Lmchorse/bbs_mod/data/types/MapType;", at = @At("HEAD"), cancellable = true)
    private static void bbspp$getPrivateClipboardMapWithKey(String verificationKey, CallbackInfoReturnable<MapType> cir)
    {
        if (BBSPrivateClipboard.isEnabled() && BBSPrivateClipboard.hasCurrentData())
        {
            cir.setReturnValue(BBSPrivateClipboard.getMap(verificationKey));
        }
    }

    /**
     * 注入目标：从系统剪贴板读取 List 数据之前。
     * 注入原因：变换等 BBS 列表数据应优先从私有剪贴板读取。
     * 修改行为：私有剪贴板有当前数据时直接返回其 List 副本；否则回退到原系统剪贴板。
     */
    @Inject(method = "getClipboardList()Lmchorse/bbs_mod/data/types/ListType;", at = @At("HEAD"), cancellable = true)
    private static void bbspp$getPrivateClipboardList(CallbackInfoReturnable<ListType> cir)
    {
        if (BBSPrivateClipboard.isEnabled() && BBSPrivateClipboard.hasCurrentData())
        {
            cir.setReturnValue(BBSPrivateClipboard.getList());
        }
    }
}
