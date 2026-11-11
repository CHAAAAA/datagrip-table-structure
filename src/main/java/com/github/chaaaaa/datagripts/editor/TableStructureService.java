package com.github.chaaaaa.datagripts.editor;

import com.github.chaaaaa.datagripts.model.TableKey;
import com.intellij.database.psi.DbTable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 项目级服务：保证同一个表/数据源只有一个虚拟文件实例，并负责把虚拟文件以编辑器 tab 打开。
 *
 * <p>原因：编辑器以 <b>VirtualFile 实例身份</b>（而非文件名）区分 tab，
 * 如果每次点击都 new 一个新的 LightVirtualFile，就会为同一张表打开多个重复 tab。</p>
 */
@Service(Service.Level.PROJECT)
public final class TableStructureService {

    private final Map<String, TableStructureVirtualFile> files = new ConcurrentHashMap<>();
    private final Map<String, ObjectBrowserVirtualFile> browsers = new ConcurrentHashMap<>();

    public static @NotNull TableStructureService getInstance(@NotNull Project project) {
        return project.getService(TableStructureService.class);
    }

    /**
     * 在编辑器区打开（或聚焦）虚拟文件对应的 tab。
     *
     * <p>263 弃用了 {@code openFile(VirtualFile, boolean)}，改用非阻塞的 {@code requestOpenFile}；
     * 但 262 只有 {@code requestOpenFile(VirtualFile)}（返回 void，内部等价 openFile(file, true)），
     * 263 是返回 CompletableFuture 的版本并新增了带 {@code FileEditorOpenRequest} 的重载，编译期没有共同签名，
     * 所以两个版本都只能反射调用。263 用 {@code withFocus(true)} 保持原来打开即聚焦的行为。</p>
     */
    public void openEditor(@NotNull Project project, @NotNull VirtualFile file) {
        FileEditorManager manager = FileEditorManager.getInstance(project);
        try {
            Object request = focusedOpenRequest();
            if (request != null) {
                FileEditorManager.class.getMethod("requestOpenFile", VirtualFile.class, request.getClass())
                        .invoke(manager, file, request);
            } else {
                FileEditorManager.class.getMethod("requestOpenFile", VirtualFile.class).invoke(manager, file);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法打开编辑器", e);
        }
    }

    /** 263 的 {@code FileEditorOpenRequest.withFocus(true)}；262 没有该类时返回 null（1 参数版本本身就聚焦）。 */
    private static @Nullable Object focusedOpenRequest() {
        // shortcut: 依赖平台类名/方法名；平台给出跨版本稳定的打开 API 后改回直接调用
        try {
            Class<?> requestType = Class.forName("com.intellij.openapi.fileEditor.FileEditorOpenRequest");
            return requestType.getMethod("withFocus", boolean.class).invoke(null, true);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** 每个数据源一个对象总览 tab。 */
    public @NotNull ObjectBrowserVirtualFile browserFor(@NotNull String dataSourceId, @NotNull String dataSourceName) {
        return browsers.computeIfAbsent(dataSourceId, ignored -> new ObjectBrowserVirtualFile(dataSourceId, dataSourceName));
    }

    /**
     * 获取（或创建）某张表对应的虚拟文件。
     *
     * @param key     表的稳定标识
     * @param pointer 指向 {@link DbTable} 的智能指针（模型刷新后仍可重新解析）
     */
    public @NotNull TableStructureVirtualFile fileFor(
            @NotNull TableKey key,
            @NotNull SmartPsiElementPointer<DbTable> pointer
    ) {
        return files.computeIfAbsent(key.id(), ignored -> new TableStructureVirtualFile(key, pointer));
    }
}
