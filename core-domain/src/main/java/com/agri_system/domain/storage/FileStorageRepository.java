package com.agri_system.domain.storage;

import java.io.InputStream;

/**
 * 文件存储仓储接口，负责与具体存储介质交互。
 */
public interface FileStorageRepository {

    /**
     * 将文件上传到存储介质，并返回可直接访问的 URL。
     *
     * @param objectKey    存储对象 Key（包含目录结构）
     * @param inputStream  文件输入流
     * @param contentLength 文件大小
     * @param contentType  ContentType
     * @return 访问 URL
     */
    String upload(String objectKey, InputStream inputStream, long contentLength, String contentType);
}





