package com.open.terminal.openterminal.model;

import javafx.beans.property.SimpleStringProperty;

/**
 * @description: TODO
 * @author huangjialong
 * @date 2026/1/4 16:48
 * @version 1.0
 */
public class RemoteFile {
    private final SimpleStringProperty fileName;
    private final SimpleStringProperty size;
    private final SimpleStringProperty permissions;
    private final SimpleStringProperty modificationTime;

    private final String rawName; // 原始文件名(不含装饰)
    private final boolean isDirectory;

    public RemoteFile(String fileName, String size, String permissions, String modificationTime, String rawName, boolean isDirectory) {
        this.fileName = new SimpleStringProperty(fileName);
        this.size = new SimpleStringProperty(size);
        this.permissions = new SimpleStringProperty(permissions);
        this.modificationTime = new SimpleStringProperty(modificationTime);
        this.rawName = rawName;
        this.isDirectory = isDirectory;
    }

    public String getFileName() {
        return fileName.get();
    }

    public String getSize() {
        return size.get();
    }

    public String getPermissions() {
        return permissions.get();
    }

    public String getModificationTime() {
        return modificationTime.get();
    }

    public String getRawName() {
        return rawName;
    }

    public boolean isDirectory() {
        return isDirectory;
    }
}
