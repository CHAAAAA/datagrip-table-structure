package com.github.chaaaaa.datagripts.service;

import com.intellij.database.actions.RefreshActionsLogic;
import com.intellij.database.model.basic.BasicElement;
import com.intellij.database.model.basic.BasicModel;
import com.intellij.database.psi.DbTable;
import com.intellij.database.util.AsyncTask;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 把「刷新这一张表」翻译成 DataGrip 的 fragment 内省。
 *
 * <p>为什么不能只重新读一遍模型：模型是 DataGrip 已内省的快照，重读只会拿到同样的旧数据。
 * 要让“在库里改了表结构 → 点刷新能看到”，必须真的回到数据库重新内省这一个对象；
 * 这里走的正是 DataGrip 对象树「刷新」用的同一条路径（{@code refreshSpecificFragments}）。</p>
 *
 * <p>注意：{@code DbTable.getDasObject()} 返回的是 DbTable 自身（DasSymbol 的 default 实现），
 * 真正的模型对象要用 {@code getDelegate()} 取，运行时是 {@link BasicElement}。</p>
 */
public final class TableRefreshService {

    private static final Logger LOG = Logger.getInstance(TableRefreshService.class);

    private TableRefreshService() {
    }

    /**
     * 从数据库重新内省一张表，完成后回调 {@code onFinished}（在后台线程池上，调用方需自行切 EDT）。
     *
     * <p>不抛异常：内省发起失败只记日志并立刻回调，让面板退化到「重新读模型」，
     * 不至于让刷新按钮直接报错。</p>
     *
     * @return {@code true} 已发起数据库内省；{@code false} 无法发起（调用方只需重新读模型）
     */
    public static boolean refreshFromDatabase(@NotNull Project project, @NotNull DbTable table,
                                             @NotNull Runnable onFinished) {
        BasicElement element;
        try {
            element = basicElementOf(table);
        } catch (Exception | LinkageError e) {
            LOG.warn("无法读取表对象模型，将只重新读取模型", e);
            onFinished.run();
            return false;
        }
        if (element == null) {
            onFinished.run();
            return false;
        }

        AsyncTask<?> task;
        try {
            BasicModel model = element.getModel();
            // 依赖 com.intellij.database 内部 API，跨大版本可能变；失败不能连带搞崩结构 tab
            task = model == null
                    ? null
                    : RefreshActionsLogic.refreshSpecificFragments(project, model, List.of(element));
        } catch (Exception | LinkageError e) {
            LOG.warn("无法发起表结构刷新，将只重新读取模型", e);
            onFinished.run();
            return false;
        }
        if (task == null) {
            // null 是平台唯一的失败信号（模型未注册 / 非本地数据源 / 该 DBMS 不支持片段内省）
            LOG.info("当前对象不支持从数据库单独刷新，将只重新读取模型");
            onFinished.run();
            return false;
        }
        task.whenComplete(AppExecutorUtil.getAppExecutorService(), (result, error) -> {
            if (error != null) {
                LOG.warn("重新内省表结构失败，将只重新读取模型", error);
            }
            onFinished.run();
        });
        return true;
    }

    /** 表的模型对象；不是 {@link BasicElement}（少数方言/假对象）时返回 null。 */
    static @Nullable BasicElement basicElementOf(@NotNull DbTable table) {
        Object delegate = table.getDelegate();
        return delegate instanceof BasicElement element ? element : null;
    }
}
