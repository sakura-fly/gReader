package com.gg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * gReader 主窗口 —— 整合所有组件和交互逻辑。
 * <p>
 * 使用无边框窗口（undecorated frame）以支持：
 * <ul>
 *   <li>窗口透明度调节（Ctrl+滚轮 / Ctrl++/-）</li>
 *   <li>边框切换（快捷键 'o'）—— 隐藏后只显示文本区域</li>
 * </ul>
 * <p>
 * 快捷键一览：
 * <ul>
 *   <li>j / 下 / 右 / PageDown / 空格 — 下一页</li>
 *   <li>k / 上 / 左 / PageUp — 上一页</li>
 *   <li>c — 切换目录面板</li>
 *   <li>o — 切换边框（菜单栏+标题栏）</li>
 *   <li>Ctrl+加号 — 增加不透明度</li>
 *   <li>Ctrl+减号 — 减少不透明度</li>
 *   <li>Ctrl+滚轮 — 调节不透明度</li>
 * </ul>
 */
public class ReaderWindow extends JFrame {
    private static final int RESIZE_MARGIN = 6;

    private final ConfigManager config;
    private final TextPage textPage;
    private final TOCPanel tocPanel;
    private final TitleBar titleBar;
    private boolean borderVisible;
    private JMenuBar menuBar;
    private JMenu recentMenu;

    // 窗口拖动相关
    private Point dragStart;
    private boolean dragging;

    // 窗口缩放边缘检测，使用位标志：1=N 2=S 4=E 8=W，组合表示角落
    private int resizeDir;
    private Point resizeStart;
    private Rectangle resizeBounds;

    public ReaderWindow() {
        config = new ConfigManager();

        // 无边框窗口，支持透明度和边框切换
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("gReader");
        setOpacity(config.getOpacity());

        textPage = new TextPage(config);
        tocPanel = new TOCPanel(config, textPage);
        titleBar = new TitleBar(this);

        // 分页变化时（如缩放），自动刷新目录页码
        textPage.setOnPagesChanged(() -> tocPanel.rebuildTOC());

        setupMenuBar();
        assembleLayout();
        setupKeyBindings();
        setupDragAndDrop();
        setupWindowDrag();
        setupResizeHandles();
        setupCloseHandler();

        setBounds(config.getWindowBounds());
        borderVisible = config.isBorderVisible();

        if (!borderVisible) {
            menuBar.setVisible(false);
            titleBar.setVisible(false);
        }

        tocPanel.setVisible(config.isTocVisible());
    }

    /** 组装布局：标题栏 + 菜单栏（上）+ 文本区域（满铺），目录为悬浮覆盖层 */
    private void assembleLayout() {
        JPanel rootPanel = new JPanel(new BorderLayout());

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(titleBar, BorderLayout.NORTH);
        headerPanel.add(menuBar, BorderLayout.SOUTH);
        rootPanel.add(headerPanel, BorderLayout.NORTH);

        // 文本区域满铺，目录作为悬浮层不影响其尺寸
        rootPanel.add(textPage, BorderLayout.CENTER);

        setContentPane(rootPanel);

        // 将目录面板放入 JFrame 的层级面板（PALETTE_LAYER），悬浮显示
        JLayeredPane layeredPane = getLayeredPane();
        layeredPane.add(tocPanel, JLayeredPane.PALETTE_LAYER);

        // 窗口尺寸变化时重新定位目录面板
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                positionTocOverlay();
            }
            @Override
            public void componentMoved(ComponentEvent e) {
                positionTocOverlay();
            }
        });
    }

    /** 将目录面板定位在窗口右侧，高度与文本区域一致 */
    private void positionTocOverlay() {
        int tocWidth = 260;
        int x = getContentPane().getWidth() - tocWidth;
        int headerH = titleBar.isVisible() ? titleBar.getHeight() : 0;
        headerH += (menuBar != null && menuBar.isVisible()) ? menuBar.getHeight() : 0;
        int y = headerH;
        int h = getContentPane().getHeight() - headerH;
        tocPanel.setBounds(x, y, tocWidth, h);
    }

    /** 构建菜单栏：文件 / 视图 / 跳转 */
    private void setupMenuBar() {
        menuBar = new JMenuBar();
        menuBar.setBackground(new Color(0x3C3C3C));
        menuBar.setBorder(null);

        // ===== 文件菜单 =====
        JMenu fileMenu = createMenu("文件");
        JMenuItem openItem = createMenuItem("打开...", "control O");
        openItem.addActionListener(e -> showOpenDialog());
        fileMenu.add(openItem);

        recentMenu = createMenu("最近打开");
        fileMenu.add(recentMenu);

        JMenuItem clearRecent = createMenuItem("清空记录", null);
        clearRecent.addActionListener(e -> {
            config.clearRecentFiles();
            refreshRecentMenu();
        });
        fileMenu.add(new JSeparator());
        fileMenu.add(clearRecent);

        fileMenu.add(new JSeparator());
        JMenuItem exitItem = createMenuItem("退出", "control Q");
        exitItem.addActionListener(e -> saveAndExit());
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);

        // ===== 视图菜单 =====
        JMenu viewMenu = createMenu("视图");
        JMenuItem tocMenuItem = createMenuItem("目录", "control T");
        tocMenuItem.addActionListener(e -> toggleToc());
        viewMenu.add(tocMenuItem);

        JMenuItem borderItem = createMenuItem("切换边框", "control B");
        borderItem.addActionListener(e -> toggleBorder());
        viewMenu.add(borderItem);

        JMenuItem settingsItem = createMenuItem("设置...", null);
        settingsItem.addActionListener(e -> {
            SettingsDialog dialog = new SettingsDialog(this, config, textPage);
            dialog.setVisible(true);
            tocPanel.rebuildTOC();
        });
        viewMenu.add(settingsItem);
        menuBar.add(viewMenu);

        // ===== 跳转菜单 =====
        JMenu goMenu = createMenu("跳转");
        JMenuItem nextItem = createMenuItem("下一页", null);
        nextItem.addActionListener(e -> textPage.nextPage());
        goMenu.add(nextItem);

        JMenuItem prevItem = createMenuItem("上一页", null);
        prevItem.addActionListener(e -> textPage.prevPage());
        goMenu.add(prevItem);

        JMenuItem goToItem = createMenuItem("跳转到页面...", "control G");
        goToItem.addActionListener(e -> showGoToDialog());
        goMenu.add(goToItem);
        menuBar.add(goMenu);

        refreshRecentMenu();
    }

    private JMenu createMenu(String text) {
        JMenu menu = new JMenu(text);
        menu.setForeground(new Color(0xCCCCCC));
        return menu;
    }

    private JMenuItem createMenuItem(String text, String accelerator) {
        JMenuItem item = new JMenuItem(text);
        item.setForeground(Color.BLACK);
        if (accelerator != null) {
            item.setAccelerator(KeyStroke.getKeyStroke(accelerator));
        }
        return item;
    }

    /** 刷新最近打开文件子菜单 */
    private void refreshRecentMenu() {
        recentMenu.removeAll();
        List<String> recentFiles = config.getRecentFiles();
        if (recentFiles.isEmpty()) {
            JMenuItem empty = new JMenuItem("（空）");
            empty.setEnabled(false);
            recentMenu.add(empty);
        } else {
            for (String path : recentFiles) {
                JMenuItem item = new JMenuItem(new File(path).getName());
                item.setToolTipText(path);
                item.addActionListener(e -> openFile(new File(path)));
                recentMenu.add(item);
            }
        }
    }

    private void showOpenDialog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("文本文件 (*.txt)", "txt"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openFile(chooser.getSelectedFile());
        }
    }

    /** 跳转到指定页面的输入对话框 */
    private void showGoToDialog() {
        String input = JOptionPane.showInputDialog(this,
                "跳转到页面（1 - " + textPage.getTotalPages() + "）：",
                "跳转到页面", JOptionPane.QUESTION_MESSAGE);
        if (input != null && !input.isBlank()) {
            try {
                int page = Integer.parseInt(input.trim()) - 1;
                textPage.goToPage(page);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /**
     * 打开文件：读取文本 → 设置到 TextPage → 更新标题和配置 → 重建目录。
     * 编码优先级：UTF-8，失败时回退到系统默认编码。
     */
    void openFile(File file) {
        try {
            String text = readTextFile(file);
            textPage.setText(text);
            textPage.goToPage(0);
            titleBar.setTitle(file.getName());
            config.setLastFilePath(file.getAbsolutePath());
            config.setLastPage(0);
            config.addRecentFile(file.getAbsolutePath());
            refreshRecentMenu();
            tocPanel.rebuildTOC();
            tocPanel.refreshListSelection();
            textPage.repaint();
            textPage.requestFocusInWindow();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "打开文件失败：\n" + ex.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 读取文本文件：UTF-8 优先，失败时回退系统默认编码 */
    private String readTextFile(File file) throws IOException {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            return Files.readString(file.toPath(), Charset.defaultCharset());
        }
    }

    /** 设置全局快捷键，绑定到 textPage（WHEN_IN_FOCUSED_WINDOW 作用域） */
    private void setupKeyBindings() {
        InputMap im = textPage.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = textPage.getActionMap();

        // 翻页快捷键
        im.put(KeyStroke.getKeyStroke('j'), "nextPage");
        im.put(KeyStroke.getKeyStroke('k'), "prevPage");
        im.put(KeyStroke.getKeyStroke("PAGE_DOWN"), "nextPage");
        im.put(KeyStroke.getKeyStroke("PAGE_UP"), "prevPage");
        im.put(KeyStroke.getKeyStroke("RIGHT"), "nextPage");
        im.put(KeyStroke.getKeyStroke("LEFT"), "prevPage");
        im.put(KeyStroke.getKeyStroke("DOWN"), "nextPage");
        im.put(KeyStroke.getKeyStroke("UP"), "prevPage");
        im.put(KeyStroke.getKeyStroke("SPACE"), "nextPage");

        // 功能快捷键
        im.put(KeyStroke.getKeyStroke('c'), "toggleToc");
        im.put(KeyStroke.getKeyStroke('o'), "toggleBorder");

        // 透明度快捷键
        im.put(KeyStroke.getKeyStroke("control EQUALS"), "increaseOpacity");
        im.put(KeyStroke.getKeyStroke("control PLUS"), "increaseOpacity");
        im.put(KeyStroke.getKeyStroke("control MINUS"), "decreaseOpacity");

        am.put("nextPage", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { textPage.nextPage(); }
        });
        am.put("prevPage", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { textPage.prevPage(); }
        });
        am.put("toggleToc", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { toggleToc(); }
        });
        am.put("toggleBorder", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { toggleBorder(); }
        });
        am.put("increaseOpacity", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { adjustOpacity(0.05f); }
        });
        am.put("decreaseOpacity", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { adjustOpacity(-0.05f); }
        });
    }

    /** 设置拖放导入：从文件管理器拖入 .txt 文件到文本区域 */
    private void setupDragAndDrop() {
        textPage.setTransferHandler(new TransferHandler() {
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                try {
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        File f = files.get(0);
                        if (f.getName().toLowerCase().endsWith(".txt")) {
                            openFile(f);
                            return true;
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                return false;
            }
        });
    }

    /** 设置鼠标拖动窗口（从文本区域拖动移动窗口）和 Ctrl+滚轮调节透明度 */
    private void setupWindowDrag() {
        textPage.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (resizeDir != 0) return; // 正在调整大小时不触发拖动
                dragStart = e.getLocationOnScreen();
                dragging = false;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = false;
            }
        });

        textPage.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (resizeDir != 0) return;
                if (dragStart == null) return;
                if ((getExtendedState() & MAXIMIZED_BOTH) != 0) return;
                dragging = true;
                Point p = e.getLocationOnScreen();
                int dx = p.x - dragStart.x;
                int dy = p.y - dragStart.y;
                Point loc = getLocation();
                setLocation(loc.x + dx, loc.y + dy);
                dragStart = p;
            }
        });

        // Ctrl+鼠标滚轮 → 调节透明度
        textPage.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                double delta = e.getPreciseWheelRotation() < 0 ? 0.05 : -0.05;
                adjustOpacity((float) delta);
                e.consume();
            }
        });
    }

    /** 设置窗口边缘拖拽调整大小（无边框窗口需要自行实现） */
    private void setupResizeHandles() {
        textPage.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (resizeDir != 0
                        && (getExtendedState() & MAXIMIZED_BOTH) == 0) {
                    resizeStart = e.getLocationOnScreen();
                    resizeBounds = getBounds();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                resizeDir = 0;
                resizeStart = null;
                textPage.setCursor(Cursor.getDefaultCursor());
            }
        });

        // 鼠标移动：检测是否在边缘，切换光标
        textPage.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (dragging) return;
                int dir = getResizeDirection(e.getPoint());
                if (dir != resizeDir) {
                    resizeDir = dir;
                    textPage.setCursor(getResizeCursor(dir));
                }
            }

            // 鼠标拖动：根据方向调整窗口大小
            @Override
            public void mouseDragged(MouseEvent e) {
                if (resizeStart != null && resizeDir != 0) {
                    Point p = e.getLocationOnScreen();
                    int dx = p.x - resizeStart.x;
                    int dy = p.y - resizeStart.y;
                    Rectangle r = new Rectangle(resizeBounds);

                    if ((resizeDir & 8) != 0) { r.x += dx; r.width -= dx; }   // 左边缘
                    if ((resizeDir & 4) != 0) { r.width += dx; }              // 右边缘
                    if ((resizeDir & 1) != 0) { r.y += dy; r.height -= dy; }  // 上边缘
                    if ((resizeDir & 2) != 0) { r.height += dy; }             // 下边缘

                    // 限制最小尺寸
                    if (r.width < 300) r.width = 300;
                    if (r.height < 200) r.height = 200;
                    setBounds(r);
                }
            }
        });
    }

    /** 检测鼠标位置对应的边缘方向（6px 触发区域） */
    private int getResizeDirection(Point p) {
        int dir = 0;
        int w = textPage.getWidth();
        int h = textPage.getHeight();
        if (p.y < RESIZE_MARGIN) dir |= 1;        // 上
        if (p.y > h - RESIZE_MARGIN) dir |= 2;    // 下
        if (p.x < RESIZE_MARGIN) dir |= 8;        // 左
        if (p.x > w - RESIZE_MARGIN) dir |= 4;    // 右
        return dir;
    }

    private Cursor getResizeCursor(int dir) {
        return switch (dir) {
            case 1, 2 -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case 4, 8 -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            case 1 | 4, 2 | 8 -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            case 1 | 8, 2 | 4 -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            default -> Cursor.getDefaultCursor();
        };
    }

    /** 窗口关闭时保存当前状态 */
    private void setupCloseHandler() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                restoreSession(); // 窗口显示布局完成后恢复，确保 textPage 已有有效尺寸
            }
            @Override
            public void windowClosing(WindowEvent e) {
                saveState();
            }
        });
    }

    /** 保存窗口边界、页码、透明度、各开关状态 */
    private void saveState() {
        if ((getExtendedState() & MAXIMIZED_BOTH) == 0) {
            config.setWindowBounds(getBounds());
        }
        config.setLastPage(textPage.getCurrentPage());
        config.setOpacity(getOpacity());
        config.setBorderVisible(borderVisible);
        config.setTocVisible(tocPanel.isVisible());
        config.flush(); // 强制写入磁盘
    }

    private void saveAndExit() {
        saveState();
        dispose();
        System.exit(0);
    }

    /** 切换目录悬浮面板并更新位置 */
    private void toggleToc() {
        tocPanel.toggle();
        if (tocPanel.isVisible()) {
            positionTocOverlay();
        }
    }

    /** 切换边框：隐藏/显示菜单栏和标题栏 */
    void toggleBorder() {
        borderVisible = !borderVisible;
        if (menuBar != null) menuBar.setVisible(borderVisible);
        titleBar.setVisible(borderVisible);
        config.setBorderVisible(borderVisible);
        revalidate();
        textPage.recalculatePages();
        textPage.repaint();
    }

    /** 调节窗口透明度，范围限制在 10%（0.1）~ 100%（1.0） */
    void adjustOpacity(float delta) {
        float newOpacity = Math.max(0.1f, Math.min(1.0f, getOpacity() + delta));
        setOpacity(newOpacity);
        config.setOpacity(newOpacity);
    }

    /** 恢复上次会话：打开上次关闭时的文件和页码 */
    private void restoreSession() {
        String lastFile = config.getLastFilePath();
        if (lastFile != null) {
            File f = new File(lastFile);
            if (f.exists()) {
                int savedPage = config.getLastPage();  // 在 openFile 覆盖前保存
                openFile(f);
                config.setLastPage(savedPage);          // 恢复正确的页码
                textPage.goToPage(savedPage);
                textPage.repaint();
            }
        }
    }
}
