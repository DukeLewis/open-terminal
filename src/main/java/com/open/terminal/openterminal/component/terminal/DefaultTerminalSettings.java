package com.open.terminal.openterminal.component.terminal;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class DefaultTerminalSettings extends DefaultSettingsProvider {
    @Override
    public float getTerminalFontSize() {
        return 15f; // 字体大小
    }

    // 关闭鼠标事件报告
    @Override
    public boolean enableMouseReporting() {
        return false;
    }

    // 设置字体方法
    @Override
    public Font getTerminalFont() {
        // 优先尝试程序员专用等宽字体
        // 顺序：JetBrains Mono -> Fira Code -> Consolas -> 默认等宽
        String[] fonts = {"JetBrains Mono", "Fira Code", "Cascadia Code", "Consolas", "Monospaced"};
        for (String fontName : fonts) {
            Font font = new Font(fontName, Font.PLAIN, (int) getTerminalFontSize());
            // 检查字体是否真的存在
            if (font.getFamily().equals(fontName)) {
                return font;
            }
        }
        return new Font("Monospaced", Font.PLAIN, (int) getTerminalFontSize());
    }

    // 前景色
    @NotNull
    @Override
    public TerminalColor getDefaultForeground() {
        // 灰白
        return new TerminalColor(198, 204, 215);
    }

    // 背景色
    @NotNull
    @Override
    public TerminalColor getDefaultBackground() {
        return new TerminalColor(255, 255, 255);
    }

    /**
     * 选中文字的颜色
     */
    @Override
    public @NotNull TextStyle getSelectionColor() {
        return new TextStyle(
                TerminalColor.WHITE,
                // 选中背景：深蓝色 (经典的选中高亮色)
                TerminalColor.rgb(38, 79, 120)
        );
    }

    /**
     * 搜索结果高亮颜色
     */
    @Override
    public @NotNull TextStyle getFoundPatternColor() {
        return new TextStyle(
                TerminalColor.BLACK,
                // 搜索背景：亮黄色
                TerminalColor.rgb(242, 201, 76)
        );
    }

    /**
     * 超链接颜色
     */
    @Override
    public TextStyle getHyperlinkColor() {
        return new TextStyle(
                // 链接色：淡蓝色
                TerminalColor.rgb(88, 166, 255),
                TerminalColor.rgb(30, 30, 30)
        );
    }

    /**
     * 开启抗锯齿，让字体更平滑
     */
    @Override
    public boolean useAntialiasing() {
        return true;
    }

    /**
     * 增加行间距，让代码不那么拥挤 (UserSettingProvider 接口中的默认方法)
     * 注意：如果你的 jediterm 版本较老可能没有这个 override，如果报错请删除
     */
    @Override
    public float getLineSpacing() {
        return 1.1f; // 1.1倍行距
    }

    /**
     * 增大缓冲区行数，方便回滚查看日志
     */
    @Override
    public int getBufferMaxLinesCount() {
        return 10000;
    }

    /**
     * 光标闪烁速度 (毫秒)，稍微慢一点显得稳重
     */
    @Override
    public int caretBlinkingMs() {
        return 800;
    }

    /**
     * 鼠标滚轮在 Vim/Less 等程序中是否发送箭头键模拟滚动
     */
    @Override
    public boolean simulateMouseScrollWithArrowKeysInAlternativeScreen() {
        return true;
    }
}
