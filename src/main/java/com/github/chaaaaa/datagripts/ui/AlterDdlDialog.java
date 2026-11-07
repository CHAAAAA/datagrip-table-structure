package com.github.chaaaaa.datagripts.ui;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTextArea;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.datatransfer.StringSelection;

/**
 * Oracle 新增列 DDL 的只读预览与复制，不提供执行入口。
 */
public final class AlterDdlDialog {

    private AlterDdlDialog() {
    }

    public static void show(@Nullable Project project, @NotNull String ddl) {
        JTextArea area = new JTextArea(ddl);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, area.getFont().getSize()));
        area.setBorder(JBUI.Borders.empty(4));
        area.setCaretPosition(0);

        JBScrollPane scroll = new JBScrollPane(area);
        scroll.setPreferredSize(new Dimension(JBUI.scale(720), JBUI.scale(320)));

        DialogBuilder builder = project == null ? new DialogBuilder() : new DialogBuilder(project);
        builder.setTitle("Oracle 新增列 DDL 预览");
        builder.setCenterPanel(scroll);
        builder.setPreferredFocusComponent(area);
        builder.addCloseButton();
        builder.addLeftSideAction(new javax.swing.AbstractAction("复制全部") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                CopyPasteManager.getInstance().setContents(new StringSelection(ddl));
            }
        });
        builder.show();
    }
}
