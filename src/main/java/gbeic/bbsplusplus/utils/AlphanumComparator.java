package gbeic.bbsplusplus.utils;

import java.util.Comparator;

/**
 * 这是一个“自然排序”（Alphanum Sort）比较器，
 * 用法与 Windows 资源管理器的文件排序一致，能将字符串中的数字按数值大小进行比较，
 * 例如：效果2 会排在 效果10 前面。
 */
public class AlphanumComparator implements Comparator<String> {

    private boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    private String getChunk(String s, int slength, int marker) {
        StringBuilder chunk = new StringBuilder();
        char c = s.charAt(marker);
        chunk.append(c);
        marker++;
        if (isDigit(c)) {
            while (marker < slength) {
                c = s.charAt(marker);
                if (!isDigit(c)) {
                    break;
                }
                chunk.append(c);
                marker++;
            }
        } else {
            while (marker < slength) {
                c = s.charAt(marker);
                if (isDigit(c)) {
                    break;
                }
                chunk.append(c);
                marker++;
            }
        }
        return chunk.toString();
    }

    @Override
    public int compare(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0;
        }

        int thisMarker = 0;
        int thatMarker = 0;
        int s1Length = s1.length();
        int s2Length = s2.length();

        while (thisMarker < s1Length && thatMarker < s2Length) {
            String thisChunk = getChunk(s1, s1Length, thisMarker);
            thisMarker += thisChunk.length();

            String thatChunk = getChunk(s2, s2Length, thatMarker);
            thatMarker += thatChunk.length();

            // 如果两个 chunk 都是数字，则按数值比较
            int result = 0;
            if (isDigit(thisChunk.charAt(0)) && isDigit(thatChunk.charAt(0))) {
                // 去除前导零，以便比较纯数值长度
                int thisChunkLength = thisChunk.length();
                result = thisChunkLength - thatChunk.length();
                // 如果数值长度相同，则从头逐位比较
                if (result == 0) {
                    for (int i = 0; i < thisChunkLength; i++) {
                        result = thisChunk.charAt(i) - thatChunk.charAt(i);
                        if (result != 0) {
                            return result;
                        }
                    }
                }
            } else {
                result = thisChunk.compareToIgnoreCase(thatChunk);
            }

            if (result != 0) {
                return result;
            }
        }

        return s1Length - s2Length;
    }
}
