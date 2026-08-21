package gbeic.bbsplusplus.client.ui.film;

import mchorse.bbs_mod.utils.DataPath;

import java.util.Collection;

/**
 * 暴露给影片库布局使用的路径列表增强能力。
 * <p>
 * 原版 {@code UIDataPathList} 只面向当前文件夹做名称过滤。影片库第一阶段需要在不改数据格式的前提下
 * 支持完整路径搜索与本地排序偏好，因此通过接口把这些影片库专属行为从通用列表里隔离出来。
 * </p>
 */
public interface IFilmLibraryPathList
{
    /** 标记该列表由影片库使用，启用完整路径显示、路径搜索和排序偏好。 */
    void bbspp$setFilmLibraryMode(boolean enabled);

    /** @return 当前列表是否启用了新版影片库模式。 */
    boolean bbspp$isFilmLibraryMode();

    /** 按名称或完整路径过滤影片，空字符串则恢复当前文件夹列表。 */
    void bbspp$filterFilmLibrary(String query);

    /** 设置名称排序方向。 */
    void bbspp$setFilmLibrarySortDescending(boolean descending);

    /** @return 当前是否按名称降序排列。 */
    boolean bbspp$isFilmLibrarySortDescending();

    /** 显示全部影片，右侧列表不显示文件夹。 */
    void bbspp$showAllFilmLibraryFilms();

    /** 显示指定文件夹直属影片，子文件夹只在左侧树中呈现。 */
    void bbspp$showFilmLibraryFolder(DataPath folder);

    /** @return 影片仓库中的所有文件夹路径。 */
    Collection<DataPath> bbspp$getFilmLibraryFolders();

    /** @return 当前右侧列表是否处于全部影片视图。 */
    boolean bbspp$isShowingAllFilmLibraryFilms();

    /** @return 当前右侧列表对应的文件夹。 */
    DataPath bbspp$getFilmLibraryFolder();

    /** 设置刷新后需要自动选中的影片 ID。 */
    void bbspp$selectFilmLibraryAfterRefresh(String id);

    /** @return 指定文件夹是否没有直属或嵌套的影片/子文件夹。 */
    boolean bbspp$isFilmLibraryFolderEmpty(DataPath folder);

    /** 记录一个文件夹路径，避免原版刷新漏掉刚被清空的空文件夹。 */
    void bbspp$rememberFilmLibraryFolder(DataPath folder);

    /** 移除已知文件夹及其子文件夹，通常用于真实删除文件夹后同步左侧树。 */
    void bbspp$forgetFilmLibraryFolder(DataPath folder);

    /** 文件夹重命名后同步已知文件夹缓存。 */
    void bbspp$renameKnownFilmLibraryFolder(DataPath folder, String name);
}
