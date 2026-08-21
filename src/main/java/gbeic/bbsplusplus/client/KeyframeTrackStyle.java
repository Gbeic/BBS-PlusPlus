package gbeic.bbsplusplus.client;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import gbeic.bbsplusplus.KeyframeLocalizer;
import gbeic.bbsplusplus.forms.AAAParticleForm;
import gbeic.bbsplusplus.forms.ItemSprayForm;
import gbeic.bbsplusplus.forms.VideoBillboardForm;
import gbeic.bbsplusplus.api.KeyframeTrackExtensionRegistry;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;

/**
 * BBS++ 关键帧轨道样式辅助。
 *
 * 解决的问题：部分表单复用了 {@code speed/range/radius/billboard} 等通用属性名，
 * 如果只按属性名全局分配轨道名称、颜色和图标，会误伤其它形态的同名轨道。
 *
 * 实现思路：根据 {@link UIKeyframeSheet#property} 反查所属形态，只在属于指定 BBS++
 * 表单时应用专属轨道名称和样式，避免同名属性的全局样式互相污染。
 */
public class KeyframeTrackStyle
{
    public static void apply(UIKeyframeSheet sheet)
    {
        if (sheet == null || sheet.property == null)
        {
            return;
        }

        String key = getKey(sheet);
        Form form = FormUtils.getForm(sheet.property);
        KeyframeTrackExtensionRegistry.Extension extension = KeyframeTrackExtensionRegistry.get(key);

        if (extension != null && extension.styleSource() != null && !extension.styleSource().isEmpty())
        {
            sheet.color = UIReplaysEditor.getColor(extension.styleSource());
            sheet.icon(UIReplaysEditor.getIcon(extension.styleSource()));
        }

        if (extension != null && extension.color() != null)
        {
            sheet.color = extension.color();
        }

        if (extension != null && extension.icon() != null)
        {
            sheet.icon(extension.icon());
        }

        if (form instanceof ItemSprayForm)
        {
            String title = KeyframeLocalizer.localizeItemSpray(sheet.id);

            if (title != null)
            {
                sheet.title = IKey.constant(title);
            }
        }
        else if (form instanceof AAAParticleForm)
        {
            String title = KeyframeLocalizer.localizeAAAParticle(sheet.id);

            if (title != null)
            {
                sheet.title = IKey.constant(title);
            }
        }
        else if (form instanceof VideoBillboardForm)
        {
            String title = KeyframeLocalizer.localizeVideoBillboard(sheet.id);

            if (title != null)
            {
                sheet.title = IKey.constant(title);
            }
        }

        Integer color = getColor(form, key);

        if (color != null)
        {
            sheet.color = color;
        }

        Icon icon = getIcon(form, key);

        if (icon != null)
        {
            sheet.icon(icon);
        }
    }

    public static Icon getIconOverride(UIKeyframeSheet sheet)
    {
        if (sheet == null || sheet.property == null)
        {
            return null;
        }

        String key = getKey(sheet);
        KeyframeTrackExtensionRegistry.Extension extension = KeyframeTrackExtensionRegistry.get(key);

        if (extension != null && extension.icon() != null)
        {
            return extension.icon();
        }

        if (extension != null && extension.styleSource() != null && !extension.styleSource().isEmpty())
        {
            return UIReplaysEditor.getIcon(extension.styleSource());
        }

        return getIcon(FormUtils.getForm(sheet.property), key);
    }

    private static String getKey(UIKeyframeSheet sheet)
    {
        return StringUtils.fileName(sheet.id);
    }

    private static Integer getColor(Form form, String key)
    {
        if (form instanceof ItemSprayForm)
        {
            return getItemSprayColor(key);
        }

        if (form instanceof AAAParticleForm)
        {
            return getAAAParticleColor(key);
        }

        if (form instanceof VideoBillboardForm)
        {
            return getVideoBillboardColor(key);
        }

        return null;
    }

    private static Icon getIcon(Form form, String key)
    {
        if (form instanceof ItemSprayForm)
        {
            return getItemSprayIcon(key);
        }

        if (form instanceof AAAParticleForm)
        {
            return getAAAParticleIcon(key);
        }

        if (form instanceof VideoBillboardForm)
        {
            return getVideoBillboardIcon(key);
        }

        return null;
    }

    private static Integer getItemSprayColor(String key)
    {
        switch (key)
        {
            case "amount":
                return Colors.BLUE;
            case "range":
            case "radius":
            case "spawnWidth":
            case "spawnHeight":
            case "spawnOffset":
            case "scatter":
                return Colors.GREEN;
            case "speed":
            case "speedOffset":
            case "gravitySpeed":
            case "rotationSpeedX":
            case "rotationSpeedY":
            case "rotationSpeedZ":
            case "rotationRandomSpeed":
            case "itemScale":
            case "scaleScatter":
            case "scaleInTime":
                return Colors.CYAN;
            case "frequency":
            case "lifetime":
            case "previewMode":
            case "simulationTime":
                return Colors.YELLOW;
            case "gravity":
            case "collision":
            case "stopAtCenter":
                return Colors.RED;
            case "itemPitch":
            case "itemYaw":
            case "itemRoll":
                return Colors.ORANGE;
            case "color":
                return Colors.INACTIVE;
            case "seed":
            case "billboard":
            case "showGuide":
            case "emissionShape":
                return Colors.WHITE;
        }

        return null;
    }

    private static Icon getItemSprayIcon(String key)
    {
        switch (key)
        {
            case "amount":
                return Icons.MORE;
            case "range":
            case "radius":
            case "spawnWidth":
            case "spawnHeight":
            case "spawnOffset":
            case "scatter":
                return Icons.SPHERE;
            case "speed":
            case "speedOffset":
            case "gravitySpeed":
            case "rotationSpeedX":
            case "rotationSpeedY":
            case "rotationSpeedZ":
            case "rotationRandomSpeed":
                return Icons.STOPWATCH;
            case "itemScale":
            case "scaleScatter":
            case "scaleInTime":
                return Icons.SCALE;
            case "gravity":
            case "collision":
                return Icons.BLOCK;
            case "stopAtCenter":
                return Icons.STOP;
            case "frequency":
            case "lifetime":
            case "simulationTime":
                return Icons.TIME;
            case "previewMode":
                return Icons.PLAY;
            case "seed":
            case "emissionShape":
                return Icons.WRENCH;
            case "itemPitch":
            case "itemYaw":
            case "itemRoll":
                return Icons.ALL_DIRECTIONS;
            case "billboard":
                return Icons.LOOKING;
            case "showGuide":
                return Icons.VISIBLE;
            case "color":
                return Icons.BUCKET;
        }

        return null;
    }

    private static Integer getAAAParticleColor(String key)
    {
        switch (key)
        {
            case "effect":
                return Colors.MAGENTA;
            case "paused":
            case "restart":
            case "loop":
            case "loopStart":
            case "loopEnd":
                return Colors.YELLOW;
            case "speed":
            case "particleScale":
                return Colors.CYAN;
            case "trigger0":
            case "trigger1":
            case "trigger2":
            case "trigger3":
                return Colors.RED;
            case "ignoreDepth":
                return Colors.WHITE;
        }

        return null;
    }

    private static Icon getAAAParticleIcon(String key)
    {
        switch (key)
        {
            case "effect":
                return Icons.PARTICLE;
            case "paused":
                return Icons.PAUSE;
            case "restart":
                return Icons.REDO;
            case "loop":
                return Icons.REFRESH;
            case "loopStart":
                return Icons.LEFTLOAD;
            case "loopEnd":
                return Icons.RIGHTLOAD;
            case "speed":
                return Icons.STOPWATCH;
            case "particleScale":
                return Icons.SCALE;
            case "dynamicInput0":
            case "dynamicInput1":
            case "dynamicInput2":
            case "dynamicInput3":
                return Icons.WRENCH;
            case "trigger0":
            case "trigger1":
            case "trigger2":
            case "trigger3":
                return Icons.BULLET;
            case "ignoreDepth":
                return Icons.VISIBLE;
        }

        return null;
    }

    private static Integer getVideoBillboardColor(String key)
    {
        switch (key)
        {
            case "video":
                return Colors.MAGENTA;
            case "width":
            case "height":
            case "keepAspectRatio":
            case "billboard":
                return Colors.CYAN;
            case "offsetSeconds":
            case "speed":
            case "paused":
            case "restart":
            case "loop":
            case "loopStart":
            case "loopEnd":
            case "outOfRange":
                return Colors.YELLOW;
        }

        return null;
    }

    private static Icon getVideoBillboardIcon(String key)
    {
        switch (key)
        {
            case "video":
                return Icons.FILM;
            case "width":
            case "height":
                return Icons.SCALE;
            case "keepAspectRatio":
                return Icons.LINK;
            case "billboard":
                return Icons.LOOKING;
            case "offsetSeconds":
            case "loopStart":
            case "loopEnd":
                return Icons.TIME;
            case "speed":
                return Icons.STOPWATCH;
            case "paused":
                return Icons.PAUSE;
            case "restart":
                return Icons.REDO;
            case "loop":
                return Icons.REFRESH;
            case "outOfRange":
                return Icons.WRENCH;
        }

        return null;
    }
}
