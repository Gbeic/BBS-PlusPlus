package gbeic.bbsplusplus.client;

import gbeic.bbsplusplus.BBSAddonsSettings;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;

import java.util.Objects;

/**
 * 保存 BBS 专用结构化复制数据的私有剪贴板。
 * <p>
 * BBS 原版会把回放、关键帧、形态、变换等数据序列化后写入系统剪贴板，
 * 导致玩家切到其它软件时看到一大串元数据。本类只缓存 {@link MapType}
 * 和 {@link ListType} 这类 BBS 内部数据，普通文本仍交给系统剪贴板处理。
 * 同时记录复制时的系统剪贴板快照，用于在外部复制内容后自动让私有数据过期。
 * </p>
 */
public class BBSPrivateClipboard
{
    private static BaseType content;
    private static String systemClipboardSnapshot = "";
    private static boolean occupied;

    public static boolean isEnabled()
    {
        return BBSAddonsSettings.privateBbsClipboard != null && BBSAddonsSettings.privateBbsClipboard.get();
    }

    public static void copy(BaseType data)
    {
        if (data == null)
        {
            return;
        }

        content = data.copy();
        systemClipboardSnapshot = Window.getClipboard();
        occupied = true;
    }

    public static void copy(MapType data, String verificationKey)
    {
        if (data == null)
        {
            return;
        }

        MapType copy = (MapType) data.copy();

        if (verificationKey != null && !verificationKey.isEmpty())
        {
            copy.putBool(verificationKey, true);
        }

        copy(copy);
    }

    public static void clear()
    {
        content = null;
        systemClipboardSnapshot = "";
        occupied = false;
    }

    public static boolean hasCurrentData()
    {
        return ensureCurrent();
    }

    public static MapType getMap()
    {
        if (!ensureCurrent() || !(content instanceof MapType))
        {
            return null;
        }

        return (MapType) content.copy();
    }

    public static MapType getMap(String verificationKey)
    {
        MapType map = getMap();

        return map != null && map.getBool(verificationKey) ? map : null;
    }

    public static ListType getList()
    {
        if (!ensureCurrent() || !(content instanceof ListType))
        {
            return null;
        }

        return (ListType) content.copy();
    }

    private static boolean ensureCurrent()
    {
        if (!occupied)
        {
            return false;
        }

        if (!Objects.equals(systemClipboardSnapshot, Window.getClipboard()))
        {
            clear();

            return false;
        }

        return true;
    }
}
