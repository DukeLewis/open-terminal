package com.open.terminal.openterminal.util;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.CharacterIterator;

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

    /**
     * 安全创建远程目录，如果目录已存在则忽略错误
     */
    public static void safeSftpMkdir(ChannelSftp sftpChannel, String dirPath) throws SftpException {
        try {
            sftpChannel.mkdir(dirPath);
        } catch (SftpException e) {
            // JSch 的 SSH_FX_FAILURE (id=4) 通常表示目录已存在或其他一般性错误
            // 为了稳健，如果创建失败，我们可以尝试 cd 进去，如果能 cd 进去说明目录存在，否则才是真的创建失败
            if (e.id != ChannelSftp.SSH_FX_FAILURE) {
                throw e; // 抛出其他严重错误
            }

            // 二次确认：尝试获取该路径属性，用来判断是否真的存在
            try {
                sftpChannel.stat(dirPath);
                // 如果没抛异常，说明目录确实存在，忽略之前的 mkdir 错误
            } catch (SftpException checkEx) {
                // 如果 stat 也失败了，说明 mkdir 是因为其他原因失败的，必须抛出原异常
                throw e;
            }
        }
    }

    // 辅助方法：格式化文件大小，转换为二进制文件大小：KiB，MiB，GiB 等
    public static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new java.text.StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            // 位运算，二进制数右移位10位，相当于value/(2^10 = 1024)
            value >>= 10;
            ci.next();
        }
        // 恢复符号（正 / 负），bytes 可能是负数（比如差值、剩余空间）
        value *= Long.signum(bytes);
        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }
}
