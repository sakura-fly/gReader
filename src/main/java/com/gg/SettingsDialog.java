package com.gg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

/**
 * 设置对话框 —— 调整字体、颜色和自定义目录正则。
 * <p>
 * 提供以下设置项：
 * <ul>
 *   <li>字体：系统所有可用字体的下拉选择</li>
 *   <li>字号：8~72 的微调框</li>
 *   <li>文字颜色 / 背景颜色：点击色块按钮调用系统取色器</li>
 *   <li>自定义标题正则：多行文本输入，每行一个正则</li>
 * </ul>
 * 点击"应用"可实时预览效果，"确定"保存设置并关闭，"取消"放弃修改。
 */
public class SettingsDialog extends JDialog {
    private final ConfigManager config;
    private final TextPage textPage;
    private final Runnable onApplied;

    private JComboBox<String> fontFamilyCombo;
    private JSpinner fontSizeSpinner;
    private Color selectedTextColor;
    private Color selectedBgColor;
    private JButton textColorBtn;
    private JButton bgColorBtn;
    private JTextArea regexArea;
    private java.util.Map<String, JPanel> keyFields;

    public SettingsDialog(JFrame parent, ConfigManager config, TextPage textPage, Runnable onApplied) {
        super(parent, "设置", true);
        this.config = config;
        this.textPage = textPage;
        this.onApplied = onApplied;
        this.selectedTextColor = config.getTextColor();
        this.selectedBgColor = config.getBackgroundColor();

        setResizable(false);
        buildUI();
        pack();
        setLocationRelativeTo(parent);

        // ESC 关闭对话框
        getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "close");
        getRootPane().getActionMap().put("close",
                new javax.swing.AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { dispose(); }});
    }

    private void buildUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 主题选择
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel("主题："), gbc);
        String[] themes = {"默认", "IDEA Dark"};
        JComboBox<String> themeCombo = new JComboBox<>(themes);
        themeCombo.setSelectedItem(config.getTheme());
        themeCombo.addActionListener(e -> {
            String theme = (String) themeCombo.getSelectedItem();
            config.applyTheme(theme);
            selectedTextColor = config.getTextColor(); textColorBtn.repaint();
            selectedBgColor = config.getBackgroundColor(); bgColorBtn.repaint();
        });
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(themeCombo, gbc);

        // 字体选择
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("字体："), gbc);

        String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames();
        fontFamilyCombo = new JComboBox<>(fontNames);
        fontFamilyCombo.setSelectedItem(config.getFontFamily());
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(fontFamilyCombo, gbc);

        // 字号选择
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        panel.add(new JLabel("字号："), gbc);

        fontSizeSpinner = new JSpinner(new SpinnerNumberModel(config.getFontSize(), 8, 72, 1));
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(fontSizeSpinner, gbc);

        // 文字颜色
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        panel.add(new JLabel("文字颜色："), gbc);

        JPanel textColorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        textColorBtn = new ColorButton(selectedTextColor);
        textColorBtn.addActionListener(e -> chooseTextColor());
        textColorPanel.add(textColorBtn);
        textColorPanel.add(new JLabel("点击修改"));
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(textColorPanel, gbc);

        // 背景颜色
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        panel.add(new JLabel("背景颜色："), gbc);

        JPanel bgColorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bgColorBtn = new ColorButton(selectedBgColor);
        bgColorBtn.addActionListener(e -> chooseBgColor());
        bgColorPanel.add(bgColorBtn);
        bgColorPanel.add(new JLabel("点击修改"));
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(bgColorPanel, gbc);

        // 自定义正则
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        panel.add(new JLabel("自定义标题正则（每行一个）："), gbc);

        List<String> customRegexes = config.getCustomRegexes();
        regexArea = new JTextArea(String.join("\n", customRegexes), 6, 40);
        regexArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane regexScroll = new JScrollPane(regexArea);
        regexScroll.setPreferredSize(new Dimension(400, 120));
        gbc.gridy = 6;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(regexScroll, gbc);

        // 快捷键设置
        gbc.gridy = 7;
        gbc.gridwidth = 2;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel keyPanel = new JPanel(new GridBagLayout());
        keyPanel.setBorder(BorderFactory.createTitledBorder("快捷键（点击修改 / +添加 / ×删除）"));
        GridBagConstraints kg = new GridBagConstraints();
        kg.insets = new Insets(2, 4, 2, 4);
        kg.fill = GridBagConstraints.HORIZONTAL;

        String[][] keyDefs = {
            {"nextPage", "下一页", "j, PAGE_DOWN, RIGHT, DOWN, SPACE"},
            {"prevPage", "上一页", "k, PAGE_UP, LEFT, UP"},
            {"nextChapter", "下一章", "control J, control PAGE_DOWN, control DOWN"},
            {"prevChapter", "上一章", "control K, control PAGE_UP, control UP"},
            {"toggleToc", "目录", "c"},
            {"toggleBorder", "边框", "o"},
            {"increaseOpacity", "增加透明度", "control EQUALS, control PLUS"},
            {"decreaseOpacity", "减少透明度", "control MINUS"},
            {"openSettings", "打开设置", "control alt S"},
        };
        keyFields = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyDefs.length; i++) {
            String action = keyDefs[i][0];
            String label = keyDefs[i][1];
            String defKeys = keyDefs[i][2];
            kg.gridx = 0; kg.gridy = i; kg.weightx = 0;
            keyPanel.add(new JLabel(label + "："), kg);

            // 芯片面板：存放各快捷键的按钮
            JPanel chipsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            chipsPanel.setOpaque(false);

            // 用 Consumer 包装重建芯片的方法（用数组绕开自引用初始化问题）
            java.util.function.Consumer<String[]>[] holder = new java.util.function.Consumer[1];
            holder[0] = (keys) -> {
                chipsPanel.removeAll();
                for (String k : keys) {
                    if (k.isBlank()) continue;
                    JButton chip = new JButton(k.trim());
                    chip.setFont(new Font("SansSerif", Font.PLAIN, 11));
                    chip.setMargin(new Insets(0, 4, 0, 4));
                    chip.addActionListener(ev -> {
                        KeyCaptureDialog cap = new KeyCaptureDialog((JFrame) getParent());
                        cap.setVisible(true);
                        if (cap.deleted) {
                            // 收集除当前外的所有键，重建
                            java.util.List<String> list = new java.util.ArrayList<>();
                            for (Component c : chipsPanel.getComponents()) {
                                if (c instanceof JButton b && b != chip) list.add(b.getText());
                            }
                            holder[0].accept(list.toArray(new String[0]));
                        } else if (cap.captured != null) {
                            chip.setText(cap.captured);
                        }
                    });
                    chipsPanel.add(chip);
                }
                chipsPanel.revalidate();
                chipsPanel.repaint();
            };

            String currentKeys = config.getKeyBinding(action, defKeys);
            holder[0].accept(currentKeys.split(",\\s*"));

            // 添加按钮
            JButton addBtn = new JButton("+");
            addBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
            addBtn.setMargin(new Insets(0, 4, 0, 4));
            addBtn.addActionListener(e -> {
                KeyCaptureDialog cap = new KeyCaptureDialog((JFrame) getParent());
                cap.setVisible(true);
                if (cap.captured != null) {
                    java.util.List<String> list = new java.util.ArrayList<>();
                    for (Component c : chipsPanel.getComponents()) {
                        if (c instanceof JButton b) list.add(b.getText());
                    }
                    list.add(cap.captured);
                    holder[0].accept(list.toArray(new String[0]));
                }
            });

            // 默认按钮
            JButton resetBtn = new JButton("默认");
            resetBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
            resetBtn.setMargin(new Insets(0, 4, 0, 4));
            resetBtn.addActionListener(ev -> holder[0].accept(defKeys.split(",\\s*")));

            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            rightPanel.setOpaque(false);
            rightPanel.add(chipsPanel);
            rightPanel.add(addBtn);
            rightPanel.add(resetBtn);

            kg.gridx = 1; kg.weightx = 1;
            keyPanel.add(rightPanel, kg);
            keyFields.put(action, chipsPanel);
        }
        panel.add(keyPanel, gbc);

        // 底部按钮：确定 / 应用 / 取消
        gbc.gridy = 8;
        gbc.gridwidth = 2;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("确定");
        JButton applyBtn = new JButton("应用");
        JButton cancelBtn = new JButton("取消");

        okBtn.addActionListener(e -> {
            applySettings();
            dispose();
            //textPage.recalculatePages();
            //textPage.repaint();
        });
        applyBtn.addActionListener(e -> applySettings());
        cancelBtn.addActionListener(e -> dispose());

        buttonPanel.add(okBtn);
        buttonPanel.add(applyBtn);
        buttonPanel.add(cancelBtn);

        panel.add(buttonPanel, gbc);

        add(panel);
    }

    private void chooseTextColor() {
        Color c = JColorChooser.showDialog(this, "文字颜色", selectedTextColor);
        if (c != null) {
            selectedTextColor = c;
            textColorBtn.repaint();
        }
    }

    private void chooseBgColor() {
        Color c = JColorChooser.showDialog(this, "背景颜色", selectedBgColor);
        if (c != null) {
            selectedBgColor = c;
            bgColorBtn.repaint();
        }
    }

    /** 将设置保存到 ConfigManager 并立即应用到 TextPage，实现实时预览 */
    private void applySettings() {
        config.setFontFamily((String) fontFamilyCombo.getSelectedItem());
        config.setFontSize((Integer) fontSizeSpinner.getValue());
        config.setTextColor(selectedTextColor);
        config.setBackgroundColor(selectedBgColor);
        config.setCustomRegexes(Arrays.asList(regexArea.getText().split("\n")));

        // 保存快捷键（从芯片面板收集各按钮文本）
        for (var entry : keyFields.entrySet()) {
            java.util.List<String> keys = new java.util.ArrayList<>();
            for (java.awt.Component c : entry.getValue().getComponents()) {
                if (c instanceof JButton b) keys.add(b.getText());
            }
            config.setKeyBinding(entry.getKey(), String.join(", ", keys));
        }

        Font newFont = new Font(config.getFontFamily(), Font.PLAIN, config.getFontSize());
        int savedChar = textPage.getCurrentPageStartChar();  // 保存当前位置
        textPage.setReaderFont(newFont);
        textPage.repositionAtChar(savedChar);               // 显式恢复
        textPage.cancelResizeTimer();
        textPage.setTextColor(selectedTextColor);
        textPage.setBackgroundColor(selectedBgColor);

        if (onApplied != null) onApplied.run();
    }

    /** 自定义颜色选择按钮：绘制色块表示当前颜色 */
    private static class ColorButton extends JButton {
        private Color color;

        ColorButton(Color color) {
            this.color = color;
            setPreferredSize(new Dimension(32, 24));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(color);
            g.fillRect(4, 4, getWidth() - 8, getHeight() - 8);
            g.setColor(Color.GRAY);
            g.drawRect(4, 4, getWidth() - 8, getHeight() - 8);
        }
    }

    /** 将 KeyStroke 转为可存储的字符串形式 */
    private static String keyStrokeToString(KeyStroke ks) {
        return ks.toString().replace("pressed ", "").replace("typed ", "").replace("released ", "");
    }

    /** 按键捕获对话框，含取消和删除按钮 */
    private class KeyCaptureDialog extends JDialog {
        String captured;
        boolean deleted;

        KeyCaptureDialog(JFrame parent) {
            super(parent, "修改快捷键", true);
            setUndecorated(true);
            JPanel p = new JPanel(new BorderLayout(10, 10));
            p.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

            JLabel label = new JLabel("按下新快捷键替换，或删除", SwingConstants.CENTER);
            label.setFont(new Font("SansSerif", Font.PLAIN, 14));
            p.add(label, BorderLayout.CENTER);

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            JButton delBtn = new JButton("删除");
            delBtn.addActionListener(e -> { deleted = true; dispose(); });
            JButton cancelBtn = new JButton("取消");
            cancelBtn.addActionListener(e -> dispose());
            btns.add(delBtn);
            btns.add(cancelBtn);
            p.add(btns, BorderLayout.SOUTH);

            add(p);
            pack();
            setLocationRelativeTo(parent);

            var ref = new Object() { KeyEventDispatcher disp; };
            ref.disp = e -> {
                if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {
                    int code = e.getKeyCode();
                    // 跳过纯修饰键，等待组合键
                    if (code != java.awt.event.KeyEvent.VK_CONTROL
                            && code != java.awt.event.KeyEvent.VK_SHIFT
                            && code != java.awt.event.KeyEvent.VK_ALT
                            && code != java.awt.event.KeyEvent.VK_META
                            && code != 0) {
                        KeyStroke ks = KeyStroke.getKeyStrokeForEvent(e);
                        captured = keyStrokeToString(ks);
                        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(ref.disp);
                        dispose();
                    }
                }
                return true;
            };
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(ref.disp);

            // 鼠标捕获
            // 滚轮捕获
            addMouseWheelListener(e -> {
                String mod = e.isControlDown() ? "ctrl " : "";
                mod += e.isShiftDown() ? "shift " : "";
                mod += e.isAltDown() ? "alt " : "";
                String dir = e.getWheelRotation() < 0 ? "WHEEL_UP" : "WHEEL_DOWN";
                captured = (mod + dir).trim();
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(ref.disp);
                dispose();
            });

            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (e.getButton() > 5) return;
                    String mod = e.isControlDown() ? "ctrl " : "";
                    mod += e.isShiftDown() ? "shift " : "";
                    mod += e.isAltDown() ? "alt " : "";
                    captured = (mod + "BUTTON" + e.getButton()).trim();
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(ref.disp);
                    dispose();
                }
            });

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(ref.disp);
                }
            });
        }
    }
}
