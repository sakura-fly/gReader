package com.gg;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * gReader 文本阅读器入口。
 * 设置系统外观后在事件分发线程中启动主窗口。
 */
public class Main {
    public static void main(String[] args) {
        // 使用系统外观，使菜单和控件风格与操作系统一致
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        // 在事件分发线程（EDT）中创建并显示窗口
        SwingUtilities.invokeLater(() -> {
            ReaderWindow window = new ReaderWindow();
            window.setVisible(true);
        });
    }
}
