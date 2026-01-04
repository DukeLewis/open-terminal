package com.open.terminal.openterminal.fun;

import com.open.terminal.openterminal.model.RemoteFile;

import java.nio.file.Path;

/**
 * @description: TODO
 * @author huangjialong
 * @date 2026/1/4 17:48
 * @version 1.0
 */
public interface FileProcessInterface {
    void openRemoteFileWithChooser(RemoteFile file);
    void downloadRemoteFileWithProgress(String remotePath, Path localPath, long totalSize) throws Exception;
    void handleFileList();
    void handleUploadFile();
}
