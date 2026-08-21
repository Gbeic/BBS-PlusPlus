package gbeic.bbsplusplus.mixin;

import gbeic.bbsplusplus.api.KeyframeTrackExtensionRegistry;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(value = UIReplaysEditor.class, remap = false)
public class UIReplaysEditorMixin
{
    /**
     * 注入目标：UIReplaysEditor 收集完模型属性轨道后。
     * 注入原因：依赖 Mod 新增属性默认位于属性列表末尾，需要按扩展注册的锚点调整位置。
     * 修改行为：在同一形态路径内，把扩展轨道移动到指定原版轨道之前。
     */
    @Inject(method = "collectFormPropertySheets", at = @At("TAIL"))
    private void bbspp$orderExtendedTracks(List<UIKeyframeSheet> sheets, Map<UIKeyframeSheet, List<UIKeyframeSheet>> poseTabs,
                                            Map<UIKeyframeSheet, Integer> poseTabDepths, CallbackInfo ci)
    {
        for (UIKeyframeSheet sheet : new ArrayList<>(sheets))
        {
            String id = StringUtils.fileName(sheet.id);
            KeyframeTrackExtensionRegistry.Extension extension = KeyframeTrackExtensionRegistry.get(id);

            if (extension == null || extension.before() == null || extension.before().isEmpty())
            {
                continue;
            }

            String parent = bbspp$parentPath(sheet.id);
            int anchor = -1;

            for (int i = 0; i < sheets.size(); i++)
            {
                UIKeyframeSheet candidate = sheets.get(i);

                if (parent.equals(bbspp$parentPath(candidate.id)) && extension.before().equals(StringUtils.fileName(candidate.id)))
                {
                    anchor = i;
                    break;
                }
            }

            if (anchor >= 0)
            {
                sheets.remove(sheet);
                anchor = sheets.indexOf(sheets.stream()
                    .filter((candidate) -> parent.equals(bbspp$parentPath(candidate.id)) && extension.before().equals(StringUtils.fileName(candidate.id)))
                    .findFirst().orElse(null));
                sheets.add(Math.max(0, anchor), sheet);
            }
        }
    }

    @Unique
    private static String bbspp$parentPath(String id)
    {
        int slash = id.lastIndexOf('/');

        return slash < 0 ? "" : id.substring(0, slash);
    }

    @Inject(method = "getColor", at = @At("HEAD"), cancellable = true)
    private static void bbspp$getAAAParticleColor(String key, CallbackInfoReturnable<Integer> cir)
    {
        String topLevel = StringUtils.fileName(key);

        switch (topLevel)
        {
            case "effect":
                cir.setReturnValue(Colors.MAGENTA);
                break;
            case "restart":
            case "loopStart":
            case "loopEnd":
                cir.setReturnValue(Colors.YELLOW);
                break;
            case "particleScale":
                cir.setReturnValue(Colors.CYAN);
                break;
            case "trigger0":
            case "trigger1":
            case "trigger2":
            case "trigger3":
                cir.setReturnValue(Colors.RED);
                break;
            case "ignoreDepth":
                cir.setReturnValue(Colors.WHITE);
                break;
        }
    }

    @Inject(method = "getIcon", at = @At("HEAD"), cancellable = true)
    private static void bbspp$getAAAParticleIcon(String key, CallbackInfoReturnable<Icon> cir)
    {
        String topLevel = StringUtils.fileName(key);

        switch (topLevel)
        {
            case "effect":
                cir.setReturnValue(Icons.PARTICLE);
                break;
            case "restart":
                cir.setReturnValue(Icons.REDO);
                break;
            case "loopStart":
                cir.setReturnValue(Icons.LEFTLOAD);
                break;
            case "loopEnd":
                cir.setReturnValue(Icons.RIGHTLOAD);
                break;
            case "particleScale":
                cir.setReturnValue(Icons.SCALE);
                break;
            case "dynamicInput0":
            case "dynamicInput1":
            case "dynamicInput2":
            case "dynamicInput3":
                cir.setReturnValue(Icons.WRENCH);
                break;
            case "trigger0":
            case "trigger1":
            case "trigger2":
            case "trigger3":
                cir.setReturnValue(Icons.BULLET);
                break;
            case "ignoreDepth":
                cir.setReturnValue(Icons.VISIBLE);
                break;
        }
    }
}
