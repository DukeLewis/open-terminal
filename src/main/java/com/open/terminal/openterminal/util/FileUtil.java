package com.open.terminal.openterminal.util;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @description:
 * @author：dukelewis
 * @date: 2025/12/29
 * @Copyright： https://github.com/DukeLewis
 */
public class FileUtil {
    // 定义本地下载目录
    public final static Path localDownloadDir = Paths.get(System.getProperty("java.io.tmpdir"), "remote-files");


    public static void openWithSystemChooser(File file) throws IOException {
        if (!Desktop.isDesktopSupported()) {
            throw new UnsupportedOperationException("当前系统不支持 Desktop API");
        }

        Desktop desktop = Desktop.getDesktop();

        // 直接打开 → 系统会弹出“选择打开方式”
        desktop.open(file);
    }
}
