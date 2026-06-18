package com.gg;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * 文本渲染引擎 —— gReader 的核心组件。
 * <p>
 * 按字符粒度分页，页面间字符连续：第 N 页末字 + 1 = 第 N+1 页首字。
 * 分页分两部分计算：锚点之前（向后推算）和锚点之后（向前渲染），
 * 确保锚点始终是一个页面边界。
 * <p>
 * pageStarts 存储每页的起始字符偏移量，displayStartChar 始终等于 pageStarts[currentPage]。
 */
public class TextPage extends JPanel {
    private static final int PADDING = 24;

    private String fullText;
    private Font font;
    private Color textColor;
    private Color bgColor;
    private int currentPage;       // 当前页码（从0开始）
    private int totalPages;        // 总页数

    /** 每页起始字符偏移量（在 fullText 中的位置），大小 = totalPages */
    private List<Integer> pageStarts = new ArrayList<>();
    /** 每个原始行（按 \n 拆分）的起始字符偏移量，供目录跳转使用 */
    private List<Integer> lineStartChars = new ArrayList<>();

    private final Timer resizeTimer;

    public TextPage(ConfigManager config) {
        this.font = new Font(config.getFontFamily(), Font.PLAIN, config.getFontSize());
        this.textColor = config.getTextColor();
        this.bgColor = config.getBackgroundColor();
        this.currentPage = 0;
        this.totalPages = 0;

        setBackground(bgColor);
        setFocusable(true);

        resizeTimer = new Timer(150, e -> {
            recalculatePages();
            repaint();
        });
        resizeTimer.setRepeats(false);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeTimer.restart();
            }
        });
    }

    /** 加载新文本，锚点重置到开头 */
    public void setText(String text) {
        this.fullText = text;
        recalculatePages(0);
    }

    public String getFullText() { return fullText; }
    public int getCurrentPage()  { return currentPage; }
    public int getTotalPages()   { return totalPages; }

    public void nextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++;
            repaint();
        }
    }

    public void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            repaint();
        }
    }

    public void goToPage(int page) {
        if (page >= 0 && page < totalPages) {
            currentPage = page;
            repaint();
        }
    }

    /** 根据原始行号计算所在页码 */
    public int getPageForLine(int originalLine) {
        if (originalLine < 0 || originalLine >= lineStartChars.size()) return 0;
        int charOffset = lineStartChars.get(originalLine);
        for (int i = pageStarts.size() - 1; i >= 0; i--) {
            if (pageStarts.get(i) <= charOffset) return i;
        }
        return 0;
    }

    public void setReaderFont(Font f) {
        this.font = f;
        recalculatePages(pageStarts.isEmpty() ? 0 : pageStarts.get(currentPage));
        repaint();
    }

    public void setTextColor(Color c) { this.textColor = c; repaint(); }

    public void setBackgroundColor(Color c) {
        this.bgColor = c;
        setBackground(c);
        repaint();
    }

    /**
     * 重新计算分页，以 anchorChar 为锚点。
     * 分两部分：向后推算锚点之前的页，向前渲染锚点之后的页。
     */
    private void recalculatePages(int anchorChar) {
        pageStarts.clear();
        lineStartChars.clear();

        if (fullText == null || fullText.isEmpty()) {
            totalPages = 0;
            currentPage = 0;
            repaint();
            return;
        }

        int usableWidth = getWidth() - 2 * PADDING;
        int usableHeight = getHeight() - 2 * PADDING;
        int textLen = fullText.length();
        if (usableWidth < 10 || usableHeight < 10) {
            totalPages = 0;
            currentPage = 0;
            repaint();
            return;
        }

        FontMetrics fm = getFontMetrics(font);
        int maxLinesPerPage = Math.max(1, usableHeight / fm.getHeight());

        // 预处理原始行偏移量（供目录跳转）
        int off = 0;
        for (int i = 0; i < textLen; ) {
            lineStartChars.add(i);
            int nl = fullText.indexOf('\n', i);
            if (nl < 0) break;
            i = nl + 1;
        }
        if (fullText.endsWith("\n")) lineStartChars.add(textLen);
        if (lineStartChars.isEmpty()) lineStartChars.add(0);

        // 边界保护
        if (anchorChar < 0) anchorChar = 0;
        if (anchorChar > textLen) anchorChar = textLen;

        // ========== 第一部分：向后推算锚点之前的页 ==========
        List<Integer> backward = new ArrayList<>();
        int curEnd = anchorChar;
        // 估算每页平均字符数，供向后推算时用作初始猜测
        int avgCharWidth = Math.max(1, fm.charWidth('a'));
        int estCharsPerLine = Math.max(1, usableWidth / avgCharWidth);
        int estCharsPerPage = estCharsPerLine * maxLinesPerPage;

        while (curEnd > 0) {
            int guess = Math.max(0, curEnd - estCharsPerPage);
            // 迭代调整猜测值，使渲染终点逼近 curEnd
            for (int iter = 0; iter < 20; iter++) {
                int end = renderUntilFull(guess, usableWidth, maxLinesPerPage, fm);
                if (end == curEnd) break;
                if (end < curEnd) {
                    guess = Math.min(curEnd - 1, guess + Math.max(1, (curEnd - end) / 2));
                } else {
                    guess = Math.max(0, guess - Math.max(1, (end - curEnd) / 2));
                }
            }
            if (guess >= curEnd) guess = Math.max(0, curEnd - 1);
            backward.add(0, guess);
            curEnd = guess;
        }

        // ========== 第二部分：向前渲染锚点之后的页 ==========
        List<Integer> forward = new ArrayList<>();
        forward.add(anchorChar);
        int pos = anchorChar;
        while (pos < textLen) {
            int linePos = pos;
            int lines = 0;
            while (lines < maxLinesPerPage && linePos < textLen) {
                linePos = nextLineEnd(linePos, usableWidth, fm);
                lines++;
            }
            if (linePos <= pos) linePos = Math.min(pos + 1, textLen);
            pos = linePos;
            if (pos < textLen) forward.add(pos);
        }

        // ========== 合并：...backward..., anchorChar, ...forward[1:]... ==========
        pageStarts.addAll(backward);
        pageStarts.addAll(forward);
        totalPages = pageStarts.size();
        currentPage = backward.size(); // 锚点页的索引
    }

    /** 缩放触发的重算，使用当前页首字符作为锚点 */
    void recalculatePages() {
        int anchor = pageStarts.isEmpty() ? 0 : pageStarts.get(currentPage);
        recalculatePages(anchor);
    }

    /**
     * 从 startPos 模拟渲染一页，返回该页结束后的字符位置。
     */
    private int renderUntilFull(int startPos, int maxWidth, int maxLines, FontMetrics fm) {
        int textLen = fullText.length();
        int pos = startPos;
        for (int line = 0; line < maxLines && pos < textLen; line++) {
            pos = nextLineEnd(pos, maxWidth, fm);
        }
        return pos;
    }

    /** 从 startPos 开始渲染一行，返回该行结束后的字符位置 */
    private int nextLineEnd(int startPos, int maxWidth, FontMetrics fm) {
        int textLen = fullText.length();
        if (startPos >= textLen) return startPos;
        if (fullText.charAt(startPos) == '\n') return startPos + 1;

        int pos = startPos;
        int lineWidth = 0;
        int lastSpace = -1;

        while (pos < textLen) {
            char c = fullText.charAt(pos);
            if (c == '\n') return pos + 1;
            int cw = fm.charWidth(c);
            if (lineWidth + cw > maxWidth && lineWidth > 0) {
                return lastSpace >= 0 ? lastSpace + 1 : pos;
            }
            lineWidth += cw;
            if (c == ' ') lastSpace = pos;
            pos++;
        }
        return pos;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2d.setColor(bgColor);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        if (fullText == null || pageStarts.isEmpty()) {
            g2d.setColor(textColor);
            g2d.setFont(font.deriveFont(14f));
            String msg = "请打开一个txt文件（通过文件菜单或拖拽导入）";
            FontMetrics fm = g2d.getFontMetrics();
            int msgW = fm.stringWidth(msg);
            g2d.drawString(msg, (getWidth() - msgW) / 2, getHeight() / 2);
            return;
        }

        g2d.setColor(textColor);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        int lineHeight = fm.getHeight();
        int ascent = fm.getAscent();
        int usableWidth = getWidth() - 2 * PADDING;
        int maxLines = Math.max(1, (getHeight() - 2 * PADDING) / lineHeight);
        int textLen = fullText.length();

        int pos = pageStarts.get(currentPage);
        int lineY = PADDING + ascent;

        for (int line = 0; line < maxLines && pos < textLen; line++) {
            if (fullText.charAt(pos) == '\n') {
                pos++;
                lineY += lineHeight;
                continue;
            }

            int lineStart = pos, lineWidth = 0, lastSpace = -1, lineEnd = pos;
            while (pos < textLen) {
                char c = fullText.charAt(pos);
                if (c == '\n') { lineEnd = pos; pos++; break; }
                int cw = fm.charWidth(c);
                if (lineWidth + cw > usableWidth && lineWidth > 0) {
                    if (lastSpace >= 0) { lineEnd = lastSpace; pos = lastSpace + 1; }
                    else lineEnd = pos;
                    break;
                }
                lineWidth += cw;
                if (c == ' ') lastSpace = pos;
                pos++;
            }
            if (pos >= textLen && lineEnd <= lineStart) lineEnd = textLen;

            g2d.drawString(fullText.substring(lineStart, lineEnd), PADDING, lineY);
            lineY += lineHeight;
            if (pos >= textLen) break;
        }

        // 页码指示器
        String pageInfo = (currentPage + 1) + " / " + totalPages;
        g2d.setColor(new Color(textColor.getRed(), textColor.getGreen(),
                textColor.getBlue(), 128));
        Font sf = font.deriveFont(Font.PLAIN, 11f);
        g2d.setFont(sf);
        int iw = g2d.getFontMetrics().stringWidth(pageInfo);
        g2d.drawString(pageInfo, (getWidth() - iw) / 2, getHeight() - 8);
    }
}
