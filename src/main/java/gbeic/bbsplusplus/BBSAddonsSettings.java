package gbeic.bbsplusplus;

import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/**
 * 存储 BBS-Addons 添加的自定义设置项。
 * <p>
 * 由于 Mixin 无法在编译时向目标类添加字段供外部直接引用，
 * 因此通过此类持有设置值引用，其他 Mixin 和代码通过此类访问设置。
 * </p>
 */
public class BBSAddonsSettings
{
    /** BBS 增强功能标题 */
    public static ValueBoolean titleBbsEnhancements;


    /** 开启后，波浪号循环切换编辑器时跳过动作剪辑时间轴，只在相机与回放编辑器之间切换 */
    public static ValueBoolean cycleCompact;

    /** 开启后，关键帧轨道名称显示为中文 */
    public static ValueBoolean chineseKeyframeNames;

    /** 打开影片编辑器时自动切换旁观模式，关闭时恢复。关闭后需手动切换游戏模式 */
    public static ValueBoolean filmAutoGameMode;

    /** 开启后，禁止在关键帧编辑器中将关键帧拖动或添加到0秒之前（负数时间） */
    public static ValueBoolean preventNegativeKeyframes;

    /** 开启后，在影片编辑界面按住 Ctrl 并滚动鼠标滚轮时，反转时间线的前进/后退方向 */
    public static ValueBoolean reverseTimelineScroll;

    /** 开启后，按住 Shift 在3D视图中点击模型部位，不再弹出骨骼层级菜单，而是直接选中其父级骨骼 */
    public static ValueBoolean directParentPicking;

    /** 开启后，第一人称回放时恢复原版视角晃动 */
    public static ValueBoolean firstPersonBobbing;

    /** 开启后，伪装界面将使用全新的双栏布局 */
    public static ValueBoolean newMorphingPanel;

    /** 开启后，影片选择界面将使用新版影片库布局和交互 */
    public static ValueBoolean newFilmLibraryUi;

    /** 开启后，如果安装了 Iris 模组，会在影片编辑器中显示光影控制按钮 */
    public static ValueBoolean enableIrisButton;

    /** 开启后，曲线剪辑添加光影曲线时使用按光影设置分类的新选择界面 */
    public static ValueBoolean shaderCurvePicker;

    /** 开启后，关键帧编辑器布局锁定时也会隐藏轨道名称宽度的调节柄 */
    public static ValueBoolean enableUiKeyframesLayoutLock;
    
    /** 开启后，右 Ctrl 在世界内播放影片时也会应用曲线剪辑里的光影参数 */
    public static ValueBoolean worldFilmShaderCurves;

    /** 开启后，允许把整段剪辑拖到时间线顶部之外以创建更多轨道 */
    public static ValueBoolean allowClipTrackExpansion;

    /** 开启后，BBS 结构化复制数据会保存在 BBS++ 私有剪贴板中，不再污染系统剪贴板 */
    public static ValueBoolean privateBbsClipboard;

    /** 撤销历史记录最多保留的操作步数，超过后丢弃最旧记录 */
    public static ValueInt undoHistoryLimit;

    /** 影片编辑器关键帧视图中 Alt+滚轮的行为模式 */
    public static ValueInt filmAltWheelTimelineMode;



    /* 物品喷射 */
    public static ValueBoolean titleItemSpray;

    /** 开启后，物品喷射粒子在视野外时会跳过渲染 */
    public static ValueBoolean itemSprayFrustumCulling;

    /** 物品喷射粒子的最大渲染距离，0 表示跟随客户端视距 */
    public static ValueInt itemSprayMaxRenderDistance;

    /** 每帧最多渲染的物品喷射粒子数量，0 表示不限 */
    public static ValueInt itemSprayMaxRenderedItems;

    /** IR Lights 阴影烘焙中最多让多少个物品喷射粒子参与投影，0 表示禁用 */
    public static ValueInt itemSprayIRLiteShadowMaxItems;

    /* Gizmo 修改 */
    public static ValueBoolean titleGizmoModifications;

    /**
     * 开启后启用 Blockbench 风格的 Gizmo 交互模式：
     * <ul>
     *   <li>默认 TRANSLATE 模式（而非 COMBINED）</li>
     *   <li>T 键循环切换 TRANSLATE → SCALE → ROTATE</li>
     *   <li>G 键：第 1 次按→TRANSLATE，第 2 次→屏幕空间平移，第 3 次→恢复</li>
     *   <li>S 键：按→SCALE，再按→恢复</li>
     *   <li>R 键：按→ROTATE，再按→恢复</li>
     * </ul>
     */
    public static ValueBoolean gizmoBlockbenchMode;

    /** 仅在 gizmoBlockbenchMode 开启时生效。开启后 T 键循环包含 COMBINED（4 模式），关闭后 3 模式 */
    public static ValueBoolean gizmoTCombined;

    /** 仅在 gizmoBlockbenchMode 开启时生效。开启后第 2 次按 G/S/R 不恢复模式，而是调用原版 BBS 功能 */
    public static ValueBoolean gizmoKeepOriginal;

    /**
     * 获取影片编辑器关键帧视图中 Alt+滚轮的语义模式。
     * <p>
     * 设置系统用整数保存，业务代码统一通过该枚举读取，避免在交互逻辑中散落魔法数字。
     * </p>
     */
    public static AltWheelTimelineMode getFilmAltWheelTimelineMode()
    {
        if (filmAltWheelTimelineMode == null)
        {
            return AltWheelTimelineMode.DEFAULT;
        }

        return AltWheelTimelineMode.fromIndex(filmAltWheelTimelineMode.get());
    }

    /** 影片编辑器关键帧视图中 Alt+滚轮的行为模式。 */
    public enum AltWheelTimelineMode
    {
        /** 保持原版行为：未选中关键帧时调整轨道高度，选中关键帧时移动关键帧。 */
        DEFAULT,

        /** 未选中关键帧时禁用 Alt+滚轮，选中关键帧时仍移动关键帧。 */
        DISABLED,

        /** 未选中关键帧时改为左右滚动时间线，选中关键帧时仍移动关键帧。 */
        HORIZONTAL_SCROLL;

        public static AltWheelTimelineMode fromIndex(int index)
        {
            AltWheelTimelineMode[] values = values();

            return index >= 0 && index < values.length ? values[index] : DEFAULT;
        }
    }

    /* 隐藏设置项 */
    
    /** 纹理管理器的排版模式：0=列表，1=小网格，2=中网格，3=大网格 */
    public static ValueInt textureManagerLayout;

    /** AAA 粒子特效选择窗口保存的宽度，0 表示使用默认比例 */
    public static ValueInt aaaEffectPickerWidth;

    /** AAA 粒子特效选择窗口保存的高度，0 表示使用默认比例 */
    public static ValueInt aaaEffectPickerHeight;
    
    /** 影片库排序模式：0=名称升序，1=名称降序 */
    public static ValueInt filmLibrarySortMode;

    /** 影片库默认打开位置：all 或 folder:路径 */
    public static ValueString filmLibraryDefaultLocation;

    /** 新版伪装界面默认打开分类：home 或分类可见性 ID */
    public static ValueString morphingDefaultCategory;

}
