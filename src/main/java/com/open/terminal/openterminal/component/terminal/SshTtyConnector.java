package com.open.terminal.openterminal.component.terminal;

import com.jcraft.jsch.ChannelShell;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class SshTtyConnector implements TtyConnector {
    private static final Logger log = LoggerFactory.getLogger(SshTtyConnector.class);

    private final ChannelShell channel;
    private final InputStreamReader reader;
    private final OutputStream outputStream;

    public SshTtyConnector(ChannelShell channel) throws IOException {
        this.channel = channel;
        // JSch 的输入流是字节流，JediTerm 读取字符流，需要转换，指定 UTF-8 防止乱码
        this.reader = new InputStreamReader(channel.getInputStream(), StandardCharsets.UTF_8);
        this.outputStream = channel.getOutputStream();
    }

    @Override
    public void close() {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
    }

    @Override
    public String getName() {
        return "SSH";
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        log.info("写入字符：{}", bytes);
        outputStream.write(bytes);
        outputStream.flush();
    }

    @Override
    public void write(String string) throws IOException {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return channel != null && channel.isConnected();
    }

    @Override
    public int waitFor() throws InterruptedException {
        return 0;
    }

    @Override
    public boolean ready() throws IOException {
        return false;
    }

    // 关键：当终端窗口大小改变时，通知 SSH 服务器调整 PTY 大小
    // 否则 Vim 等全屏软件会显示错乱
    @Override
    public void resize(@NotNull TermSize termSize) {
        if (channel != null && channel.isConnected()) {
            // JSch 需要四个参数：列数, 行数, 像素宽, 像素高
            // 我们从 TermSize 获取列和行，像素宽高传 0 即可
            channel.setPtySize(
                    termSize.getColumns(),
                    termSize.getRows(),
                    0,
                    0
            );
        }
    }
}
