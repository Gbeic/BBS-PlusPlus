package gbeic.bbsplusplus.util;

import gbeic.bbsplusplus.client.ui.BBSUiRestoreScreen;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class IrisHelper {
    private static boolean initialized = false;
    private static Method toggleShadersMethod;
    private static Method getIrisConfigMethod;
    private static Method areShadersEnabledMethod;
    private static Constructor<?> shaderScreenConstructor;

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            getIrisConfigMethod = irisClass.getMethod("getIrisConfig");
            Object irisConfig = getIrisConfigMethod.invoke(null);
            areShadersEnabledMethod = irisConfig.getClass().getMethod("areShadersEnabled");
            toggleShadersMethod = irisClass.getMethod("toggleShaders", Minecraft.class, boolean.class);

            Class<?> screenClass = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
            shaderScreenConstructor = screenClass.getConstructor(Screen.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 通过反射调用 Iris 内部方法来开启或关闭着色器
     */
    public static void toggleShaders() {
        init();
        try {
            if (getIrisConfigMethod != null && areShadersEnabledMethod != null && toggleShadersMethod != null) {
                Object irisConfig = getIrisConfigMethod.invoke(null);
                boolean enabled = (boolean) areShadersEnabledMethod.invoke(irisConfig);
                toggleShadersMethod.invoke(null, Minecraft.getInstance(), !enabled);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取当前着色器是否开启
     */
    public static boolean isShadersEnabled() {
        init();
        try {
            if (getIrisConfigMethod != null && areShadersEnabledMethod != null) {
                Object irisConfig = getIrisConfigMethod.invoke(null);
                return (boolean) areShadersEnabledMethod.invoke(irisConfig);
            }
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * 通过反射直接实例化 ShaderPackScreen 并延迟显示
     * <p>
     * 关键点是传给 ShaderPackScreen 的 parent 不能是当前的 BBS {@link UIScreen} 实例。
     * BBS 的 UIScreen 是一次性的：被顶掉时 {@code removed()} 会把 {@code removed} 标记
     * 永久置位并拆解输入分发与菜单，之后按 Esc 返回该实例只会得到一个吞掉所有输入的黑屏。
     * 因此改为传入 {@link BBSUiRestoreScreen}，由它在显示时用 BBS 的正规入口重新打开菜单。
     * </p>
     */
    public static void openShaderScreen() {
        init();
        try {
            if (shaderScreenConstructor != null) {
                UIBaseMenu menu = UIScreen.getCurrentMenu();
                // 菜单取不到时退回当前界面，至少不改变原有行为
                Screen parent = menu == null
                    ? Minecraft.getInstance().screen
                    : new BBSUiRestoreScreen(menu);
                Object screen = shaderScreenConstructor.newInstance(parent);
                // 延迟到下一帧切换界面，避免在 UI 事件分发循环中破坏迭代器导致主线程死锁
                Minecraft.getInstance().execute(() -> {
                    Minecraft.getInstance().setScreen((Screen) screen);
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
