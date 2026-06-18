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
import javax.swing.SpinnerNumberModel;

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

    private JComboBox<String> fontFamilyCombo;
    private JSpinner fontSizeSpinner;
    private Color selectedTextColor;
    private Color selectedBgColor;
    private JButton textColorBtn;
    private JButton bgColorBtn;
    private JTextArea regexArea;

    public SettingsDialog(JFrame parent, ConfigManager config, TextPage textPage) {
        super(parent, "设置", true);
        this.config = config;
        this.textPage = textPage;
        this.selectedTextColor = config.getTextColor();
        this.selectedBgColor = config.getBackgroundColor();

        setResizable(false);
        buildUI();
        pack();
        setLocationRelativeTo(parent);
    }

    private void buildUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 字体选择
        gbc.gridx = 0;
        gbc.gridy = 0;
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
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("字号："), gbc);

        fontSizeSpinner = new JSpinner(new SpinnerNumberModel(config.getFontSize(), 8, 72, 1));
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(fontSizeSpinner, gbc);

        // 文字颜色
        gbc.gridx = 0;
        gbc.gridy = 2;
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
        gbc.gridy = 3;
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
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        panel.add(new JLabel("自定义标题正则（每行一个）："), gbc);

        List<String> customRegexes = config.getCustomRegexes();
        regexArea = new JTextArea(String.join("\n", customRegexes), 6, 40);
        regexArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane regexScroll = new JScrollPane(regexArea);
        regexScroll.setPreferredSize(new Dimension(400, 120));
        gbc.gridy = 5;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(regexScroll, gbc);

        // 底部按钮：确定 / 应用 / 取消
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("确定");
        JButton applyBtn = new JButton("应用");
        JButton cancelBtn = new JButton("取消");

        okBtn.addActionListener(e -> {
            applySettings();
            dispose();
        });
        applyBtn.addActionListener(e -> applySettings());
        cancelBtn.addActionListener(e -> dispose());

        buttonPanel.add(okBtn);
        buttonPanel.add(applyBtn);
        buttonPanel.add(cancelBtn);

        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
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
        config.setCustomRegexes(
                Arrays.asList(regexArea.getText().split("\n")));

        Font newFont = new Font(config.getFontFamily(), Font.PLAIN, config.getFontSize());
        textPage.setReaderFont(newFont);
        textPage.setTextColor(selectedTextColor);
        textPage.setBackgroundColor(selectedBgColor);
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
}
