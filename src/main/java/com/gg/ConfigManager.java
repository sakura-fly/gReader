package com.gg;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * 配置管理器，使用 Java Preferences API 持久化所有应用状态。
 * 数据存储在操作系统的用户偏好目录中（Linux下为 ~/.java/.userPrefs/）。
 * <p>
 * 持久化的内容包括：
 * <ul>
 *   <li>上次打开的文件路径和页码</li>
 *   <li>窗口位置和大小</li>
 *   <li>字体、文字颜色、背景颜色</li>
 *   <li>最近打开的文件列表（最多10个）</li>
 *   <li>自定义目录匹配正则</li>
 *   <li>UI 状态（目录面板、边框、透明度）</li>
 * </ul>
 */
public class ConfigManager {
    private static final Preferences PREFS = Preferences.userNodeForPackage(ConfigManager.class);

    private static final String KEY_LAST_FILE = "lastFilePath";
    private static final String KEY_LAST_PAGE = "lastPage";
    private static final String KEY_WINDOW_X = "windowX";
    private static final String KEY_WINDOW_Y = "windowY";
    private static final String KEY_WINDOW_W = "windowW";
    private static final String KEY_WINDOW_H = "windowH";
    private static final String KEY_FONT_FAMILY = "fontFamily";
    private static final String KEY_FONT_SIZE = "fontSize";
    private static final String KEY_TEXT_COLOR = "textColor";
    private static final String KEY_BG_COLOR = "bgColor";
    private static final String KEY_RECENT_FILES = "recentFiles";
    private static final String KEY_CUSTOM_REGEXES = "customRegexes";
    private static final String KEY_TOC_VISIBLE = "tocVisible";
    private static final String KEY_BORDER_VISIBLE = "borderVisible";
    private static final String KEY_OPACITY = "opacity";

    // ==================== 文件和页码 ====================

    public String getLastFilePath() {
        return PREFS.get(KEY_LAST_FILE, null);
    }

    public void setLastFilePath(String path) {
        PREFS.put(KEY_LAST_FILE, path);
    }

    public int getLastPage() {
        return PREFS.getInt(KEY_LAST_PAGE, 0);
    }

    public void setLastPage(int page) {
        PREFS.putInt(KEY_LAST_PAGE, Math.max(0, page));
    }

    // ==================== 窗口几何 ====================

    /** 获取保存的窗口边界，无保存记录时默认900×700居中 */
    public Rectangle getWindowBounds() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = PREFS.getInt(KEY_WINDOW_W, 900);
        int h = PREFS.getInt(KEY_WINDOW_H, 700);
        int x = PREFS.getInt(KEY_WINDOW_X, (screen.width - w) / 2);
        int y = PREFS.getInt(KEY_WINDOW_Y, (screen.height - h) / 2);
        return new Rectangle(x, y, w, h);
    }

    public void setWindowBounds(Rectangle bounds) {
        PREFS.putInt(KEY_WINDOW_X, bounds.x);
        PREFS.putInt(KEY_WINDOW_Y, bounds.y);
        PREFS.putInt(KEY_WINDOW_W, bounds.width);
        PREFS.putInt(KEY_WINDOW_H, bounds.height);
    }

    // ==================== 字体和颜色 ====================

    public String getFontFamily() {
        return PREFS.get(KEY_FONT_FAMILY, "SansSerif");
    }

    public void setFontFamily(String family) {
        PREFS.put(KEY_FONT_FAMILY, family);
    }

    /** 获取字号，默认16，范围8~72 */
    public int getFontSize() {
        return PREFS.getInt(KEY_FONT_SIZE, 16);
    }

    public void setFontSize(int size) {
        PREFS.putInt(KEY_FONT_SIZE, Math.max(8, Math.min(72, size)));
    }

    /** 颜色以RGB整数形式存储 */
    public Color getTextColor() {
        int rgb = PREFS.getInt(KEY_TEXT_COLOR, Color.BLACK.getRGB());
        return new Color(rgb);
    }

    public void setTextColor(Color c) {
        PREFS.putInt(KEY_TEXT_COLOR, c.getRGB());
    }

    public Color getBackgroundColor() {
        int rgb = PREFS.getInt(KEY_BG_COLOR, Color.WHITE.getRGB());
        return new Color(rgb);
    }

    public void setBackgroundColor(Color c) {
        PREFS.putInt(KEY_BG_COLOR, c.getRGB());
    }

    // ==================== 最近打开文件 ====================

    /** 最近文件以换行符分隔，最多保存10个，最新的在最前面 */
    public List<String> getRecentFiles() {
        String raw = PREFS.get(KEY_RECENT_FILES, "");
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\n")));
    }

    /** 将文件添加到最近列表，已存在则移到最前，超过10个则截断 */
    public void addRecentFile(String path) {
        List<String> files = getRecentFiles();
        files.remove(path);       // 已存在则先移除
        files.add(0, path);       // 插入到最前面
        if (files.size() > 10) {
            files = files.subList(0, 10);
        }
        PREFS.put(KEY_RECENT_FILES, String.join("\n", files));
    }

    public void clearRecentFiles() {
        PREFS.remove(KEY_RECENT_FILES);
    }

    // ==================== 自定义目录正则 ====================

    public List<String> getCustomRegexes() {
        String raw = PREFS.get(KEY_CUSTOM_REGEXES, "");
        if (raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\n")));
    }

    public void setCustomRegexes(List<String> patterns) {
        PREFS.put(KEY_CUSTOM_REGEXES, String.join("\n", patterns));
    }

    // ==================== UI 开关状态 ====================

    public boolean isTocVisible() {
        return PREFS.getBoolean(KEY_TOC_VISIBLE, false);
    }

    public void setTocVisible(boolean v) {
        PREFS.putBoolean(KEY_TOC_VISIBLE, v);
    }

    public boolean isBorderVisible() {
        return PREFS.getBoolean(KEY_BORDER_VISIBLE, true);
    }

    public void setBorderVisible(boolean v) {
        PREFS.putBoolean(KEY_BORDER_VISIBLE, v);
    }

    /** 窗口透明度，范围 0.1（10%）~ 1.0（100%），默认完全不透明 */
    public float getOpacity() {
        return (float) PREFS.getDouble(KEY_OPACITY, 1.0);
    }

    public void setOpacity(float v) {
        PREFS.putDouble(KEY_OPACITY, Math.max(0.1f, Math.min(1.0f, v)));
    }

    // ==================== 快捷键 ====================

    /** 获取某动作的快捷键字符串，逗号分隔，如 "j, PAGE_DOWN" */
    public String getKeyBinding(String action, String defaultKeys) {
        return PREFS.get("key." + action, defaultKeys);
    }

    public void setKeyBinding(String action, String keys) {
        PREFS.put("key." + action, keys);
    }

    /** 强制将所有配置写入磁盘，防止 JVM 退出时数据丢失 */
    public void flush() {
        try {
            PREFS.flush();
        } catch (Exception ignored) {
        }
    }
}
