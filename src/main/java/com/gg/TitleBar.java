package com.gg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * 自定义标题栏，替代操作系统的原生标题栏。
 * <p>
 * 由于应用程序使用了无边框窗口（undecorated frame）
 * 以支持透明度和边框切换功能，因此需要自定义标题栏提供：
 * <ul>
 *   <li>窗口标题显示</li>
 *   <li>拖动标题栏移动窗口</li>
 *   <li>双击切换最大化/还原</li>
 *   <li>最小化、最大化/还原、关闭按钮</li>
 * </ul>
 */
public class TitleBar extends JPanel {
    private static final int HEIGHT = 30;
    private static final Color BG_COLOR = new Color(0x333333);
    private static final Color BUTTON_HOVER = new Color(0x555555);
    private static final Color CLOSE_HOVER = new Color(0xCC3333);
    private static final Color FG_COLOR = new Color(0xCCCCCC);

    private final JFrame parent;
    private final JLabel titleLabel;
    private Point mouseDownPoint;

    public TitleBar(JFrame parent) {
        this.parent = parent;
        setPreferredSize(new Dimension(0, HEIGHT));
        setBackground(BG_COLOR);
        setLayout(new BorderLayout());

        titleLabel = new JLabel("  gReader");
        titleLabel.setForeground(FG_COLOR);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        add(titleLabel, BorderLayout.WEST);

        // 右侧窗口控制按钮：最小化 / 最大化还原 / 关闭
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonPanel.setOpaque(false);

        // 最小化按钮
        buttonPanel.add(createControlButton("—", BUTTON_HOVER, () -> {
            parent.setState(Frame.ICONIFIED);
        }));
        // 最大化/还原按钮
        buttonPanel.add(createControlButton("□", BUTTON_HOVER, () -> {
            if ((parent.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) {
                parent.setExtendedState(Frame.NORMAL);
            } else {
                parent.setExtendedState(Frame.MAXIMIZED_BOTH);
            }
        }));
        // 关闭按钮 — 悬停时变红
        buttonPanel.add(createControlButton("✕", CLOSE_HOVER, () -> {
            parent.dispatchEvent(new WindowEvent(parent, WindowEvent.WINDOW_CLOSING));
        }));

        add(buttonPanel, BorderLayout.EAST);

        // 标题栏拖动：按下记录坐标，拖动时移动窗口
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                mouseDownPoint = e.getPoint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // 双击切换最大化/还原
                if (e.getClickCount() == 2) {
                    if ((parent.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) {
                        parent.setExtendedState(Frame.NORMAL);
                    } else {
                        parent.setExtendedState(Frame.MAXIMIZED_BOTH);
                    }
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // 最大化时不允许拖动
                if (mouseDownPoint != null
                        && (parent.getExtendedState() & Frame.MAXIMIZED_BOTH) == 0) {
                    Point p = e.getLocationOnScreen();
                    parent.setLocation(p.x - mouseDownPoint.x, p.y - mouseDownPoint.y);
                }
            }
        });
    }

    public void setTitle(String title) {
        titleLabel.setText("  gReader - " + title);
    }

    /** 创建无边框风格的控制按钮，悬停时高亮 */
    private JButton createControlButton(String text, Color hoverBg, Runnable action) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 14));
        btn.setForeground(FG_COLOR);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setBorderPainted(false);

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setOpaque(true);
                btn.setBackground(hoverBg);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setOpaque(false);
                btn.setBackground(null);
            }
        });

        btn.addActionListener(e -> action.run());
        return btn;
    }
}
