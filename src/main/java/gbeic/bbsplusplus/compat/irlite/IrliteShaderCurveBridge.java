package gbeic.bbsplusplus.compat.irlite;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.iris.ShaderCurves;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把新版 IRLite 迁进 BBS 设置界面的光影参数重新接入 BBS 光影曲线系统。
 * <p>
 * IRLite 把描边（outline）、体积光（VL）、阴影等参数从 Iris 设置移到了 BBS 设置，用
 * {@code qualet.irlite.IrliteConfig} 的一批 {@code public static} BBS {@code Value*} 字段持有，
 * 每帧由 IRL 自己的 UBO 读入着色器——因此直接改这些字段即可实时驱动，无需重载光影包。
 * BBS 本体的 {@link ShaderCurves} 靠扫描 shader 源码里的 {@code // [OptionAnnotatedSource} 宏标注
 * 加 Iris 滑条白名单发现参数，对这些新参数全部失效，所以曲线选择器里看不到它们。
 * </p>
 * <p>
 * 本桥接在 IRLite 存在时，把这些字段反射包装成 {@link ShaderCurves.ShaderVariable} 注入
 * {@link ShaderCurves#variableMap}（现有 CurveClip、选择面板、世界内播放自动识别），
 * 再在每帧渲染时把曲线写入的值（{@code variable.value}）同步回对应的 BBS Value 字段。
 * 同步用 {@code setRuntimeValue}：不触发通知链，因此不会把曲线动画值落盘到设置文件。
 * </p>
 */
public final class IrliteShaderCurveBridge
{
    /** IRLite 配置类的全限定名，仅在装有 IRLite 时加载 */
    private static final String CONFIG_CLASS = "qualet.irlite.IrliteConfig";

    /** 变量名 → IRLite 配置里的静态字段。用名字做键：reset 后 variableMap 里是新的 ShaderVariable
     *  实例，apply() 每次按名字现查，不持有会失效的旧实例引用。 */
    private static Map<String, Field> NAME_TO_FIELD = new LinkedHashMap<>();

    private IrliteShaderCurveBridge()
    {}

    /**
     * 反射 IRLite 配置类，把全部 BBS Value 字段注入光影曲线变量表。
     * <p>
     * 在 {@code ShaderCurves#reset()} 的 TAIL 调用（BBS++ 的 ShaderCurvesMixin），
     * reset 会清空变量表，随后立即重注入。幂等；字段尚未注册（值为 null）时跳过，
     * 留给下一次 reset 补上。
     * </p>
     */
    public static void register()
    {
        NAME_TO_FIELD = new LinkedHashMap<>();

        if (!IrliteCompat.isLoaded())
        {
            return;
        }

        try
        {
            Class<?> configClass = Class.forName(CONFIG_CLASS);

            for (Field field : configClass.getFields())
            {
                if (!Modifier.isStatic(field.getModifiers()))
                {
                    continue;
                }

                // 字段被 IRLite 的 RegisterSettingsEvent 赋值前为 null，跳过等下次 reset
                Object valueObj = field.get(null);

                if (valueObj == null)
                {
                    continue;
                }

                String name = field.getName();
                String defaultValue;
                boolean integer;

                if (valueObj instanceof ValueBoolean bool)
                {
                    // 不能 String.valueOf(true)：ShaderVariable 构造会 Float.parseFloat
                    defaultValue = bool.get() ? "1.0" : "0.0";
                    integer = true;
                }
                else if (valueObj instanceof ValueInt intValue)
                {
                    defaultValue = String.valueOf(intValue.get());
                    integer = true;
                }
                else if (valueObj instanceof ValueFloat floatValue)
                {
                    defaultValue = String.valueOf(floatValue.get());
                    integer = false;
                }
                else
                {
                    // 非三类 BBS Value 的 public static 字段，忽略
                    continue;
                }

                NAME_TO_FIELD.put(name, field);
                ShaderCurves.variableMap.putIfAbsent(name, new ShaderCurves.ShaderVariable(name, defaultValue, integer));
            }
        }
        catch (Throwable ignored)
        {
            // 反射失败只丢 IRLite 曲线，绝不能影响渲染
        }
    }

    /**
     * 曲线选择面板打开前的兜底：若尚未注册（如 reset 早于 IRLite 字段赋值），补一次。
     */
    public static void ensureRegistered()
    {
        if (NAME_TO_FIELD.isEmpty())
        {
            register();
        }
    }

    /**
     * 每帧渲染调用：把当前帧曲线写入的值同步到对应的 BBS Value 字段，并排空变量值。
     * <p>
     * 曲线动画（编辑器预览的 CurveClientClip、世界播放的 WorldFilmShaderCurveState）
     * 都只写 {@code ShaderVariable.value}，这里统一消费。读到后立即置 null，
     * 否则曲线移除后参数会永远停在上一次的值；IRLite 字段恢复为用户在设置里保存的值。
     * </p>
     */
    public static void apply()
    {
        if (NAME_TO_FIELD.isEmpty())
        {
            return;
        }

        for (Map.Entry<String, Field> entry : NAME_TO_FIELD.entrySet())
        {
            ShaderCurves.ShaderVariable variable = ShaderCurves.variableMap.get(entry.getKey());

            if (variable == null || variable.value == null)
            {
                continue;
            }

            Float value = variable.value;

            variable.value = null;

            try
            {
                Object valueObj = entry.getValue().get(null);

                if (valueObj == null)
                {
                    continue;
                }

                if (valueObj instanceof ValueFloat floatValue)
                {
                    floatValue.setRuntimeValue(value);
                }
                else if (valueObj instanceof ValueInt intValue)
                {
                    intValue.setRuntimeValue(value.intValue());
                }
                else if (valueObj instanceof ValueBoolean bool)
                {
                    bool.setRuntimeValue(value > 0);
                }
            }
            catch (Throwable ignored)
            {
                // 反射失败跳过该参数，不影响其他参数与渲染
            }
        }
    }

    /** 该变量名是否为桥接注入的 IRLite 参数（供 Iris uniform 过滤与 UI 分组用） */
    public static boolean isIrLiteVariable(String name)
    {
        return NAME_TO_FIELD.containsKey(name);
    }

    /** 供曲线选择面板分组：IR Lights > Outline / Volumetric / Shadows */
    public static List<String> categoryOf(String name)
    {
        if (name.startsWith("outline"))
        {
            return List.of("IR Lights", "Outline");
        }

        if (name.startsWith("vl"))
        {
            return List.of("IR Lights", "Volumetric");
        }

        if (name.contains("shadow") || name.startsWith("shadow"))
        {
            return List.of("IR Lights", "Shadows");
        }

        return List.of("IR Lights");
    }

    /** 供轨道名/列表显示：camelCase 字段名转可读文本，如 outlineStrength → "Outline Strength" */
    public static String displayName(String name)
    {
        if (name == null || name.isEmpty())
        {
            return name;
        }

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < name.length(); i++)
        {
            char c = name.charAt(i);

            if (i > 0 && Character.isUpperCase(c))
            {
                builder.append(' ');
            }

            builder.append(c);
        }

        return builder.toString().toLowerCase(Locale.ROOT);
    }
}
