package gbeic.bbsplusplus.forms;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.colors.Color;

/**
 * 结构表单：把一个 {@code .nbt} 结构文件当作形态渲染出来。
 * <p>
 * 移植自 BBSTools 4.1，注册 ID 与其保持一致（{@code bbs:structure}），
 * 因此用老版 BBSTools 做的工程可以直接读取。
 * </p>
 * <p>
 * 配合结构棒使用：在世界里框选一片区域导出成结构文件，再用这个表单把它当成道具摆进影片里，
 * 既能整体上关键帧做位移旋转，也能跟着形态系统做染色。
 * </p>
 */
public class StructureForm extends Form
{
    /** 结构文件路径，相对 BBS 资源目录，例如 {@code structures/structure_stick/20260727_101530.nbt} */
    public final ValueString structureFile = new ValueString("structure_file", "");

    /** 整体染色 */
    public final ValueColor color = new ValueColor("color", Color.white());

    /*
     * 以下三个字段来自 BBSTools 4.1，在那边就只有数据没有实际效果。
     * 这里保留定义是为了让老工程读取时不丢数据，但界面上不再暴露，避免误以为能用。
     */

    /** 群系 ID，预留字段，当前不参与渲染 */
    public final ValueString biomeId = new ValueString("biome_id", "");

    /** 是否自发光，预留字段，当前不参与渲染 */
    public final ValueBoolean emitLight = new ValueBoolean("emit_light", false);

    /** 自发光强度，预留字段，当前不参与渲染 */
    public final ValueInt lightIntensity = new ValueInt("light_intensity", 15);

    public StructureForm()
    {
        this.add(this.structureFile);
        this.add(this.color);

        this.biomeId.invisible();
        this.emitLight.invisible();
        this.lightIntensity.invisible();

        this.add(this.biomeId);
        this.add(this.emitLight);
        this.add(this.lightIntensity);
    }

    /**
     * 没有单独起名时，用结构文件名（去掉扩展名）作为形态的显示名。
     */
    @Override
    protected String getDefaultDisplayName()
    {
        String path = this.structureFile.get();

        if (path == null || path.isEmpty())
        {
            return super.getDefaultDisplayName();
        }

        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String prefix = slash >= 0 ? path.substring(0, slash + 1) : "";
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        String base = name.toLowerCase().endsWith(".nbt") ? name.substring(0, name.length() - 4) : name;

        return prefix + base;
    }

    /**
     * 关键帧轨道名去掉字段名里的冗余后缀，让时间轴上显示得短一些。
     */
    @Override
    public String getTrackName(String property)
    {
        int slash = property.lastIndexOf('/');
        String prefix = slash == -1 ? "" : property.substring(0, slash + 1);
        String last = slash == -1 ? property : property.substring(slash + 1);

        if ("structure_file".equals(last))
        {
            last = "structure";
        }
        else if ("biome_id".equals(last))
        {
            last = "biome";
        }

        return super.getTrackName(prefix + last);
    }
}
