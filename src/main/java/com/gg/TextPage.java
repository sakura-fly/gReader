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
    private int bgAlpha = 255;     // 背景透明度 25~255
    // 页面缓存：预加载当前页前后各一页，翻页时滑动窗口
    private final java.util.Map<Integer, PageCache> pageCache = new java.util.LinkedHashMap<>(3, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<Integer, PageCache> e) {
            return size() > 5; // 最多缓存5页
        }
    };
    private record PageCache(int charStart, int charEnd, List<String> lines) {}

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
        setOpaque(false); // per-pixel translucency
        this.bgAlpha = config.getBgAlpha();

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
        pageCache.clear();
        recalculatePages(0);
    }

    /** 预缓存一页的渲染结果 */
    private void preCachePage(int page, int usableWidth, int maxLines, FontMetrics fm) {
        if (pageCache.containsKey(page)) return;
        int textLen = fullText.length();
        int pos = pageStarts.get(page);
        List<String> lines = new ArrayList<>();
        int endPos = pos;
        for (int line = 0; line < maxLines && pos < textLen; line++) {
            if (fullText.charAt(pos) == '\n') { lines.add(""); pos++; continue; }
            int ls = pos, lw = 0, lsp = -1, le = pos;
            while (pos < textLen) {
                char c = fullText.charAt(pos);
                if (c == '\n') { le = pos; pos++; break; }
                int cw = fm.charWidth(c);
                if (lw + cw > usableWidth && lw > 0) {
                    if (lsp >= 0) { le = lsp; pos = lsp + 1; } else le = pos;
                    break;
                }
                lw += cw; if (c == ' ') lsp = pos; pos++;
            }
            if (pos >= textLen && le <= ls) le = textLen;
            lines.add(fullText.substring(ls, le));
            endPos = pos;
        }
        pageCache.put(page, new PageCache(pageStarts.get(page), endPos, lines));
    }

    public String getFullText() { return fullText; }
    public int getCurrentPage()  { return currentPage; }
    public int getTotalPages()   { return totalPages; }

    public void nextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++;
            repaint();
            if (onPageChanged != null) onPageChanged.run();
        }
    }

    public void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            repaint();
            if (onPageChanged != null) onPageChanged.run();
        }
    }

    public void goToPage(int page) {
        if (totalPages == 0) return;
        currentPage = Math.max(0, Math.min(page, totalPages - 1));
        repaint();
    }

    /** 当前页首行对应的原始行号 */
    public int getCurrentOriginalLine() {
        if (pageStarts.isEmpty()) return 0;
        int pos = pageStarts.get(currentPage);
        for (int i = lineStartChars.size() - 1; i >= 0; i--) {
            if (lineStartChars.get(i) <= pos) return i;
        }
        return 0;
    }

    /** 当前页起始字符偏移量 */
    public int getCurrentPageStartChar() {
        if (pageStarts.isEmpty() || currentPage >= pageStarts.size()) return 0;
        return pageStarts.get(currentPage);
    }

    /** 以指定字符偏移量为锚点重新分页并重绘 */
    public void repositionAtChar(int charOffset) {
        if (fullText == null || charOffset < 0) return;
        recalculatePages(Math.min(charOffset, fullText.length()));
        repaint();
    }

    /** 跳转到指定原始行，以该行为锚点重新分页，使标题显示在第一行 */
    public void jumpToLine(int originalLine) {
        if (originalLine >= 0 && originalLine < lineStartChars.size()) {
            recalculatePages(lineStartChars.get(originalLine));
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
    }

    public void setTextColor(Color c) { this.textColor = c; repaint(); }

    public void setBackgroundColor(Color c) {
        this.bgColor = c;
        repaint();
    }

    public void setBgAlpha(int alpha) {
        this.bgAlpha = alpha;
    }

    /**
     * 重新计算分页，以 anchorChar 为锚点。
     * 分两部分：向后推算锚点之前的页，向前渲染锚点之后的页。
     */
    private void recalculatePages(int anchorChar) {
        pageStarts.clear();
        lineStartChars.clear();
        pageCache.clear();

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

    /** 分页变化回调，供外部（如目录面板）刷新数据 */
    private Runnable onPagesChanged;
    /** 页面切换回调，供目录同步选中项 */
    private Runnable onPageChanged;

    /** 取消待执行的缩放重算，防止启动时覆盖已恢复的进度 */
    public void cancelResizeTimer() {
        resizeTimer.stop();
    }

    public void setOnPagesChanged(Runnable callback) {
        this.onPagesChanged = callback;
    }
    public void setOnPageChanged(Runnable callback) {
        this.onPageChanged = callback;
    }

    /** 缩放触发的重算，使用当前页首字符作为锚点 */
    void recalculatePages() {
        int anchor = pageStarts.isEmpty() ? 0 : pageStarts.get(currentPage);
        recalculatePages(anchor);
        if (onPagesChanged != null) onPagesChanged.run();
        if (onPageChanged != null) onPageChanged.run();
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

        // Src 模式替换像素，避免旧内容透过半透明背景
        g2d.setComposite(java.awt.AlphaComposite.Src);
        g2d.setColor(new Color(bgColor.getRed(), bgColor.getGreen(),
                bgColor.getBlue(), bgAlpha));
        g2d.fillRect(0, 0, getWidth(), getHeight());
        g2d.setComposite(java.awt.AlphaComposite.SrcOver); // 恢复默认混合模式

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

        // 从缓存取当前页，无则渲染并缓存
        int pageStart = pageStarts.get(currentPage);
        PageCache cache = pageCache.get(currentPage);
        if (cache == null || cache.charStart != pageStart) {
            List<String> lines = new ArrayList<>();
            int pos = pageStart, endPos = pos;
            for (int line = 0; line < maxLines && pos < textLen; line++) {
                if (fullText.charAt(pos) == '\n') { lines.add(""); pos++; continue; }
                int ls = pos, lw = 0, lsp = -1, le = pos;
                while (pos < textLen) {
                    char c = fullText.charAt(pos);
                    if (c == '\n') { le = pos; pos++; break; }
                    int cw = fm.charWidth(c);
                    if (lw + cw > usableWidth && lw > 0) {
                        if (lsp >= 0) { le = lsp; pos = lsp + 1; } else le = pos;
                        break;
                    }
                    lw += cw; if (c == ' ') lsp = pos; pos++;
                }
                if (pos >= textLen && le <= ls) le = textLen;
                lines.add(fullText.substring(ls, le));
                endPos = pos;
            }
            cache = new PageCache(pageStart, endPos, lines);
            pageCache.put(currentPage, cache);
            // 预加载下一页
            if (currentPage + 1 < totalPages) preCachePage(currentPage + 1, usableWidth, maxLines, fm);
        }

        // 绘制缓存的行
        int lineY = PADDING + ascent;
        for (String line : cache.lines) {
            if (!line.isEmpty()) g2d.drawString(line, PADDING, lineY);
            lineY += lineHeight;
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
