package gbeic.bbsplusplus.client.ui.film;

/**
 * 标记影片库搜索框启用专属按键行为。
 * <p>
 * 普通文本框按 Esc 会直接失焦；影片库搜索框需要先清空搜索内容，因此用接口只对指定实例启用该行为。
 * </p>
 */
public interface IFilmLibrarySearchBox
{
    /** 启用影片库搜索框的 Esc 清空行为。 */
    public void bbspp$setFilmLibrarySearchBox(boolean enabled);
}
