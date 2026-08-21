package gbeic.bbsplusplus.utils;

import gbeic.bbsplusplus.BBSPlusPlusMod;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * BBS 设置文件颜色数组瘦身工具。
 * <p>
 * BBSFS 的颜色设置在重复加载后可能把同一批颜色反复追加到 {@code bbs.json}。
 * 这个工具在设置解析前流式改写 {@code recent_colors} 和 {@code favorite_colors}，
 * 避免先把异常大文件完整读进内存，同时保留玩家真实使用过或收藏过的颜色。
 * </p>
 */
public final class SettingsFileSanitizer
{
    private static final String RECENT_COLORS_KEY = "\"recent_colors\"";
    private static final String FAVORITE_COLORS_KEY = "\"favorite_colors\"";
    private static final long SETTINGS_SIZE_THRESHOLD = 16L * 1024L;
    private static final int MAX_RECENT_COLORS = 33;
    private static final int MAX_FAVORITE_COLORS = 256;

    private SettingsFileSanitizer()
    {}

    public static void sanitizeBbsSettings(File file)
    {
        if (file == null || !file.isFile() || file.length() < SETTINGS_SIZE_THRESHOLD)
        {
            return;
        }

        Path source = file.toPath();
        Path temp = source.resolveSibling(file.getName() + ".bbspp.tmp");
        Path backup = nextBackupPath(file);

        try
        {
            RewriteResult result = rewriteColorLists(source, temp);

            if (!result.changed)
            {
                Files.deleteIfExists(temp);

                return;
            }

            Files.move(source, backup);

            try
            {
                Files.move(temp, source, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException e)
            {
                Files.move(backup, source, StandardCopyOption.REPLACE_EXISTING);

                throw e;
            }

            BBSPlusPlusMod.LOGGER.warn("检测到 BBS 颜色设置异常膨胀，已自动瘦身并备份原文件：{}，最近颜色：{} -> {}，收藏颜色：{} -> {}", backup, result.recentOriginalSize, result.recentNewSize, result.favoriteOriginalSize, result.favoriteNewSize);
        }
        catch (IOException e)
        {
            BBSPlusPlusMod.LOGGER.warn("瘦身 BBS 设置文件失败，继续使用原文件：{}", file.getAbsolutePath(), e);

            try
            {
                Files.deleteIfExists(temp);
            }
            catch (IOException ignored)
            {}
        }
    }

    private static Path nextBackupPath(File file)
    {
        Path directory = file.toPath().getParent();
        String name = file.getName();

        for (int i = 0; ; i++)
        {
            String suffix = i == 0 ? ".bbspp-colors-backup" : ".bbspp-colors-backup-" + i;
            Path candidate = directory.resolve(name + suffix);

            if (!Files.exists(candidate))
            {
                return candidate;
            }
        }
    }

    private static RewriteResult rewriteColorLists(Path source, Path temp) throws IOException
    {
        RewriteResult result = new RewriteResult();

        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8))
        {
            StringBuilder matched = new StringBuilder();
            int ch;

            while ((ch = reader.read()) != -1)
            {
                matched.append((char) ch);

                ColorListKind kind = getMatchedKind(matched);

                if (kind != null)
                {
                    writer.write(kind.key);

                    if (copyUntilArrayStart(reader, writer))
                    {
                        ColorArray colors = readBoundedColorArray(reader, kind);

                        writeColorArrayTail(writer, colors.values);
                        result.record(kind, colors);
                    }

                    matched.setLength(0);

                    continue;
                }

                if (!isAnyKeyPrefix(matched))
                {
                    writer.write(matched.charAt(0));
                    matched.deleteCharAt(0);

                    while (matched.length() > 0 && !isAnyKeyPrefix(matched))
                    {
                        writer.write(matched.charAt(0));
                        matched.deleteCharAt(0);
                    }
                }
            }

            if (matched.length() > 0)
            {
                writer.write(matched.toString());
            }
        }

        return result;
    }

    private static ColorListKind getMatchedKind(StringBuilder matched)
    {
        for (ColorListKind kind : ColorListKind.values())
        {
            if (kind.key.contentEquals(matched))
            {
                return kind;
            }
        }

        return null;
    }

    private static boolean isAnyKeyPrefix(StringBuilder matched)
    {
        for (ColorListKind kind : ColorListKind.values())
        {
            if (startsWith(kind.key, matched))
            {
                return true;
            }
        }

        return false;
    }

    private static boolean startsWith(String key, StringBuilder prefix)
    {
        if (prefix.length() > key.length())
        {
            return false;
        }

        for (int i = 0; i < prefix.length(); i++)
        {
            if (key.charAt(i) != prefix.charAt(i))
            {
                return false;
            }
        }

        return true;
    }

    private static boolean copyUntilArrayStart(Reader reader, Writer writer) throws IOException
    {
        int ch;

        while ((ch = reader.read()) != -1)
        {
            writer.write(ch);

            if (ch == '[')
            {
                return true;
            }
        }

        return false;
    }

    private static ColorArray readBoundedColorArray(Reader reader, ColorListKind kind) throws IOException
    {
        LinkedHashSet<Integer> colors = new LinkedHashSet<>();
        long value = 0L;
        int originalSize = 0;
        boolean negative = false;
        boolean readingNumber = false;
        int depth = 1;
        int ch;

        while ((ch = reader.read()) != -1)
        {
            if (ch == '[')
            {
                depth++;
            }
            else if (ch == ']')
            {
                if (readingNumber)
                {
                    originalSize++;
                    addColor(colors, negative ? -value : value, kind);
                    readingNumber = false;
                }

                depth--;

                if (depth == 0)
                {
                    break;
                }
            }
            else if (depth == 1 && ch == '-' && !readingNumber)
            {
                negative = true;
                value = 0L;
                readingNumber = true;
            }
            else if (depth == 1 && Character.isDigit(ch))
            {
                if (!readingNumber)
                {
                    negative = false;
                    value = 0L;
                    readingNumber = true;
                }

                value = value * 10L + (ch - '0');
            }
            else if (readingNumber)
            {
                originalSize++;
                addColor(colors, negative ? -value : value, kind);
                negative = false;
                value = 0L;
                readingNumber = false;
            }
        }

        return new ColorArray(colors, originalSize);
    }

    private static void addColor(LinkedHashSet<Integer> colors, long value, ColorListKind kind)
    {
        int color = (int) value;

        if (kind.keepLatest)
        {
            colors.remove(color);
            colors.add(color);
        }
        else if (colors.size() < kind.limit)
        {
            colors.add(color);
        }

        while (colors.size() > kind.limit)
        {
            Iterator<Integer> iterator = colors.iterator();

            if (!iterator.hasNext())
            {
                return;
            }

            iterator.next();
            iterator.remove();
        }
    }

    private static void writeColorArrayTail(Writer writer, LinkedHashSet<Integer> colors) throws IOException
    {
        int i = 0;

        for (Integer color : colors)
        {
            if (i > 0)
            {
                writer.write(", ");
            }

            writer.write(Integer.toString(color));
            i++;
        }

        writer.write(']');
    }

    private enum ColorListKind
    {
        RECENT(RECENT_COLORS_KEY, MAX_RECENT_COLORS, true),
        FAVORITE(FAVORITE_COLORS_KEY, MAX_FAVORITE_COLORS, false);

        private final String key;
        private final int limit;
        private final boolean keepLatest;

        ColorListKind(String key, int limit, boolean keepLatest)
        {
            this.key = key;
            this.limit = limit;
            this.keepLatest = keepLatest;
        }
    }

    private static class ColorArray
    {
        private final LinkedHashSet<Integer> values;
        private final int originalSize;

        private ColorArray(LinkedHashSet<Integer> values, int originalSize)
        {
            this.values = values;
            this.originalSize = originalSize;
        }
    }

    private static class RewriteResult
    {
        private boolean changed;
        private int recentOriginalSize;
        private int recentNewSize;
        private int favoriteOriginalSize;
        private int favoriteNewSize;

        private void record(ColorListKind kind, ColorArray colors)
        {
            boolean listChanged = colors.originalSize != colors.values.size();

            this.changed |= listChanged;

            if (kind == ColorListKind.RECENT)
            {
                this.recentOriginalSize = colors.originalSize;
                this.recentNewSize = colors.values.size();
            }
            else
            {
                this.favoriteOriginalSize = colors.originalSize;
                this.favoriteNewSize = colors.values.size();
            }
        }
    }
}
