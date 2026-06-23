package com.gg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/**
 * 目录面板 —— 通过正则匹配识别标题，构建可点击的目录。
 * <p>
 * 内置5种常见标题格式：中文章节、英文Chapter、Markdown标题、数字编号标题。
 * 用户可在设置中添加自定义正则表达式来匹配特殊格式。
 * 点击目录项可跳转到对应页面。
 * 快捷键 'c' 可切换显示/隐藏。
 */
public class TOCPanel extends JPanel {
    private static final int WIDTH = 260;

    /** 内置标题正则模式：中文章节、英文Chapter、Markdown、数字编号（1级和2级） */
    private static final String[] BUILTIN_PATTERNS = {
            "^\\s*第[\\d一二三四五六七八九十百千万零]+[章節节回卷]\\s*.*$",
            "^\\s*(?i)Chapter\\s+\\d+.*$",
            "^\\s*#{1,3}\\s+.*$",
            "^\\s*\\d+\\.\\s+.*$",
            "^\\s*\\d+\\.\\d+\\.?\\s+.*$"
    };

    private final TextPage textPage;
    private final ConfigManager config;
    private final DefaultListModel<TOCEntry> model;
    private final JList<TOCEntry> list;
    private boolean updatingSelection;

    public TOCPanel(ConfigManager config, TextPage textPage) {
        this.config = config;
        this.textPage = textPage;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        // 悬浮面板边框：四周带阴影效果
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xBBBBBB), 1),
                BorderFactory.createLineBorder(new Color(0xEEEEEE), 3)));

        JLabel header = new JLabel("  目录");
        header.setFont(new Font("SansSerif", Font.BOLD, 14));
        header.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        header.setOpaque(true);
        header.setBackground(new Color(0xF5F5F5));
        add(header, BorderLayout.NORTH);

        model = new DefaultListModel<>();
        list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(new Font("SansSerif", Font.PLAIN, 13));
        list.setFixedCellHeight(28);
        list.setCellRenderer(new TOCRenderer());

        // 点击目录项跳转到对应页面
        list.addListSelectionListener(e -> {
            if (updatingSelection || e.getValueIsAdjusting()) return;
            TOCEntry entry = list.getSelectedValue();
            if (entry != null) {
                textPage.jumpToLine(entry.line());
            }
        });

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * 重建目录：逐行扫描文本，用内置+自定义正则匹配标题。
     * 每条行只会被第一个匹配的正则命中（break 机制），
     * 避免同一条标题被重复添加到目录中。
     */
    public void rebuildTOC() {
        model.clear();
        String text = textPage.getFullText();
        if (text == null || text.isEmpty()) return;

        List<Pattern> patterns = buildPatterns();
        String[] lines = text.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) continue;

            for (Pattern p : patterns) {
                if (p.matcher(trimmed).matches()) {
                    int page = textPage.getPageForLine(i);
                    model.addElement(new TOCEntry(trimmed, page, i));
                    break; // 首个匹配即命中，跳出内层循环
                }
            }
        }

        if (!model.isEmpty()) {
            updatingSelection = true;
            list.setSelectedIndex(0);
            updatingSelection = false;
        }
    }

    /** 构建正则列表：先添加内置模式，再添加用户自定义模式 */
    private List<Pattern> buildPatterns() {
        List<Pattern> patterns = new ArrayList<>();
        for (String regex : BUILTIN_PATTERNS) {
            patterns.add(Pattern.compile(regex));
        }
        for (String regex : config.getCustomRegexes()) {
            if (!regex.isBlank()) {
                try {
                    patterns.add(Pattern.compile(regex.trim()));
                } catch (Exception ignored) {
                    // 忽略无效的正则表达式
                }
            }
        }
        return patterns;
    }

    /** 浏览到新章节时同步目录选中项，不触发跳转 */
    public void syncToLine(int currentLine) {
        int sel = -1;
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).line() <= currentLine) sel = i;
            else break;
        }
        if (sel >= 0) {
            updatingSelection = true;
            list.setSelectedIndex(sel);
            list.ensureIndexIsVisible(sel);
            updatingSelection = false;
        }
    }

    /** 当前行之后的下一个章节行号，无则返回 -1 */
    public int getNextChapterLine(int currentLine) {
        for (int i = 0; i < model.size(); i++) {
            int l = model.get(i).line();
            if (l > currentLine) return l;
        }
        return -1;
    }

    /** 当前行之前的上一章节行号，无则返回 -1 */
    public int getPrevChapterLine(int currentLine) {
        int prev = -1;
        for (int i = 0; i < model.size(); i++) {
            int l = model.get(i).line();
            if (l >= currentLine) break;
            prev = l;
        }
        return prev;
    }

    /** 切换目录面板的显示/隐藏 */
    public void toggle() {
        setVisible(!isVisible());
        config.setTocVisible(isVisible());
        if (getParent() != null) {
            getParent().revalidate();
        }
    }

    public void refreshListSelection() {
        if (!model.isEmpty()) {
            updatingSelection = true;
            list.setSelectedIndex(0);
            updatingSelection = false;
        }
    }

    /** 目录条目：包含标题文本、所在页码、原始行号 */
    public record TOCEntry(String title, int page, int line) {
        @Override
        public String toString() {
            return title;
        }
    }

    /** 自定义渲染器：过长的标题截断并追加省略号 */
    private static class TOCRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            if (value instanceof TOCEntry entry) {
                String display = entry.title();
                if (display.length() > 35) {
                    display = display.substring(0, 34) + "…"; // 省略号 …
                }
                label.setText(display);
            }
            label.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 4));
            return label;
        }
    }
}
