package gbeic.bbsplusplus.forms;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.colors.Color;
import gbeic.bbsplusplus.forms.values.ValueItemStacks;

/**
 * 物品喷射形态。
 *
 * 可以从锥形、平面或球体等不同发射形状中喷射出所选物品。
 */
public class ItemSprayForm extends Form
{
    private static final String L10N_NAME = "bbs.ui.forms.item_spray";
    public static final int SHAPE_CONE = 0;
    public static final int SHAPE_PLANE = 1;
    public static final int SHAPE_SPHERE_OUT = 2;
    public static final int SHAPE_SPHERE_IN = 3;
    public static final int SHAPE_COUNT = 4;

    public final ValueItemStacks items = new ValueItemStacks("items");
    
    // 每次喷射的物品数量
    public final ValueInt amount = new ValueInt("amount", 5);
    
    // 粒子从生成起点开始继续飞行的距离，所有发射形状都共用这个语义。
    public final ValueFloat range = new ValueFloat("range", 10F);

    // 发射形状。0=锥形，1=平面，2=球体向外，3=球体向内。默认锥形以保持旧版手感。
    public final ValueInt emissionShape = new ValueInt("emissionShape", SHAPE_CONE, SHAPE_CONE, SHAPE_COUNT - 1);
    
    // 锥形模式沿用旧版“半径”字段名，底层仍用它控制锥形扩散范围。
    public final ValueFloat radius = new ValueFloat("radius", 30F, 0F, 180F);

    // 非锥形模式的生成起点范围：平面用宽高；球体用偏移量，向外从中心外移，向内从射程外壳内缩。
    public final ValueFloat spawnWidth = new ValueFloat("spawnWidth", 3F, 0F, Float.POSITIVE_INFINITY);
    public final ValueFloat spawnHeight = new ValueFloat("spawnHeight", 3F, 0F, Float.POSITIVE_INFINITY);
    public final ValueFloat spawnOffset = new ValueFloat("spawnOffset", 0F, 0F, Float.POSITIVE_INFINITY);
    public final ValueBoolean stopAtCenter = new ValueBoolean("stopAtCenter", false);
    public final ValueFloat scatter = new ValueFloat("scatter", 0F);
    public final ValueFloat speed = new ValueFloat("speed", 0.5F);
    public final ValueFloat speedOffset = new ValueFloat("speedOffset", 0F);
    public final ValueBoolean gravity = new ValueBoolean("gravity", true);

    // 每 tick 增加的下落速度。默认值 0.04F 对应旧版硬编码重力。
    public final ValueFloat gravitySpeed = new ValueFloat("gravitySpeed", 0.04F);
    
    // 碰撞开关
    public final ValueBoolean collision = new ValueBoolean("collision", false);
    
    // 喷射频率 (每隔多少 tick 喷射一次)
    public final ValueInt frequency = new ValueInt("frequency", 20);
    
    // 物品存活时间 (如果是 0 则根据 range 和 speed 自动计算)
    public final ValueInt lifetime = new ValueInt("lifetime", 0);

    // 历史序列化键仍叫 previewMode，现在反向作为实时模式开关使用。默认关闭，即默认进入确定性预览。
    public final ValueBoolean previewMode = new ValueBoolean("previewMode", false);

    // 编辑器确定性预览时间。负数会导致预览无效，因此默认从 0 开始并在渲染/UI 层限制为非负数。
    public final ValueFloat simulationTime = new ValueFloat("simulationTime", 0F);

    // 确定性预览随机种子。同一时间和同一种子会得到完全一致的喷射结果
    public final ValueInt seed = new ValueInt("seed", 1);

    // 物品初始朝向，单位为角度
    public final ValueFloat itemPitch = new ValueFloat("itemPitch", 0F);
    public final ValueFloat itemYaw = new ValueFloat("itemYaw", 0F);
    public final ValueFloat itemRoll = new ValueFloat("itemRoll", 0F);

    // 固定旋转速度与随机旋转强度。随机强度默认 10F，保持旧版每轴 -10 到 10 度/刻的效果。
    public final ValueFloat rotationSpeedX = new ValueFloat("rotationSpeedX", 0F);
    public final ValueFloat rotationSpeedY = new ValueFloat("rotationSpeedY", 0F);
    public final ValueFloat rotationSpeedZ = new ValueFloat("rotationSpeedZ", 0F);
    public final ValueFloat rotationRandomSpeed = new ValueFloat("rotationRandomSpeed", 10F);

    // 开启后渲染时始终面向镜头
    public final ValueBoolean billboard = new ValueBoolean("billboard", false);

    // 物品视觉缩放。默认 1F 会保持旧版固定大小；缩放散布会在每个粒子生成时固化为随机缩放。
    public final ValueFloat itemScale = new ValueFloat("itemScale", 1F, 0F, Float.POSITIVE_INFINITY);
    public final ValueFloat scaleScatter = new ValueFloat("scaleScatter", 0F, 0F, Float.POSITIVE_INFINITY);
    public final ValueInt scaleInTime = new ValueInt("scaleInTime", 0, 0, Integer.MAX_VALUE);
    
    // 世界辅助线开关和物品颜色。表单预览界面始终显示辅助线，世界中默认不显示。
    public final ValueBoolean showGuide = new ValueBoolean("showGuide", false);
    public final ValueColor color = new ValueColor("color", Color.white());

    public ItemSprayForm()
    {
        super();

        this.add(this.items);
        this.add(this.amount);
        this.add(this.range);
        this.add(this.emissionShape);
        this.add(this.radius);
        this.add(this.spawnWidth);
        this.add(this.spawnHeight);
        this.add(this.spawnOffset);
        this.add(this.stopAtCenter);
        this.add(this.scatter);
        this.add(this.speed);
        this.add(this.speedOffset);
        this.add(this.gravity);
        this.add(this.gravitySpeed);
        this.add(this.collision);
        this.add(this.frequency);
        this.add(this.lifetime);
        this.add(this.previewMode);
        this.add(this.simulationTime);
        this.add(this.seed);
        this.add(this.itemPitch);
        this.add(this.itemYaw);
        this.add(this.itemRoll);
        this.add(this.rotationSpeedX);
        this.add(this.rotationSpeedY);
        this.add(this.rotationSpeedZ);
        this.add(this.rotationRandomSpeed);
        this.add(this.billboard);
        this.add(this.itemScale);
        this.add(this.scaleScatter);
        this.add(this.scaleInTime);
        this.add(this.showGuide);
        this.add(this.color);
    }

    @Override
    public String getDefaultDisplayName()
    {
        try
        {
            String text = mchorse.bbs_mod.l10n.L10n.lang(L10N_NAME).get();

            return text != null && !text.equals(L10N_NAME) ? text : "Item Spray";
        }
        catch (Exception e)
        {
            return "Item Spray";
        }
    }
}
