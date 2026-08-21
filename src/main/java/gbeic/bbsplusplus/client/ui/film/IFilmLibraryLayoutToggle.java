package gbeic.bbsplusplus.client.ui.film;

/**
 * 暴露影片库布局的即时同步入口。
 * <p>
 * 设置面板里的开关可能在影片选择器已经创建、甚至已经显示时发生变化。通过这个接口，
 * 通用 UI 渲染入口可以只通知实现了新版影片库布局的选择器同步状态，避免把检测逻辑扩散到其它界面。
 * </p>
 */
public interface IFilmLibraryLayoutToggle
{
    /**
     * 根据当前设置开关同步影片库布局。
     */
    void bbspp$syncFilmLibraryLayout();
}
