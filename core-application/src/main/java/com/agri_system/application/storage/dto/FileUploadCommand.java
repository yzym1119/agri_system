package com.agri_system.application.storage.dto;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * 通用文件上传命令
 */
public class FileUploadCommand {

    private final String module;
    private final String originalFilename;
    private final String contentType;
    private final long size;
    private final InputStreamSource inputStreamSource;

    public FileUploadCommand(String module,
                             String originalFilename,
                             String contentType,
                             long size,
                             InputStreamSource inputStreamSource) {
        this.module = module;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.size = size;
        this.inputStreamSource = Objects.requireNonNull(inputStreamSource, "inputStreamSource cannot be null");
    }

    public String getModule() {
        return module;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public InputStream openStream() throws IOException {
        return inputStreamSource.openStream();
    }

    @FunctionalInterface
    public interface InputStreamSource {
        InputStream openStream() throws IOException;
    }

    public static FileUploadCommand of(String module,
                                       String originalFilename,
                                       String contentType,
                                       long size,
                                       InputStreamSource inputStreamSource) {
        return new FileUploadCommand(module, originalFilename, contentType, size, inputStreamSource);
    }
}





