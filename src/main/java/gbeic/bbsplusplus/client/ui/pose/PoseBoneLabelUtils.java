package gbeic.bbsplusplus.client.ui.pose;

import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;

/**
 * 为狭窄的姿势骨骼树形列表生成兼顾首尾信息的名称。
 *
 * <p>骨骼名称的开头通常表示左右侧或所属部位，结尾通常表示具体用途，因此从中间省略
 * 比仅截掉尾部更容易区分相近骨骼。实现按字体实际像素宽度逐步保留首尾字符，保证结果
 * 不会进入右侧状态图标区域。</p>
 */
public class PoseBoneLabelUtils
{
    private static final String ELLIPSIS = "…";

    /** 按给定像素宽度生成中间省略后的骨骼名称。 */
    public static String limitMiddle(FontRenderer font, String label, int width)
    {
        if (label == null || label.isEmpty() || width <= 0)
        {
            return "";
        }

        if (font.getWidth(label) <= width)
        {
            return label;
        }

        if (font.getWidth(ELLIPSIS) > width)
        {
            return "";
        }

        int[] codePoints = label.codePoints().toArray();
        StringBuilder left = new StringBuilder();
        StringBuilder right = new StringBuilder();
        int leftIndex = 0;
        int rightIndex = codePoints.length - 1;
        boolean takeLeft = true;

        while (leftIndex <= rightIndex)
        {
            StringBuilder nextLeft = new StringBuilder(left);
            StringBuilder nextRight = new StringBuilder(right);

            if (takeLeft)
            {
                nextLeft.appendCodePoint(codePoints[leftIndex]);
            }
            else
            {
                nextRight.insert(0, Character.toChars(codePoints[rightIndex]));
            }

            String candidate = nextLeft + ELLIPSIS + nextRight;

            if (font.getWidth(candidate) > width)
            {
                break;
            }

            if (takeLeft)
            {
                left = nextLeft;
                leftIndex += 1;
            }
            else
            {
                right = nextRight;
                rightIndex -= 1;
            }

            takeLeft = !takeLeft;
        }

        return left + ELLIPSIS + right;
    }

    private PoseBoneLabelUtils()
    {}
}
