package com.agri_system.infrastructure.storage.repository;

import com.agri_system.domain.storage.FileStorageRepository;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地文件存储（开发环境 fallback）
 *
 * 由 AliyunOssConfig 在未配置 OSS 时注册，文件保存在系统临时目录下的 agri-uploads/ 子目录中。
 */
@Slf4j
public class LocalFileStorageRepository implements FileStorageRepository {

    private static final String UPLOAD_DIR = "agri-uploads";

    @Override
    public String upload(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        try {
            Path dir = Paths.get(System.getProperty("java.io.tmpdir"), UPLOAD_DIR);
            Files.createDirectories(dir);

            // 安全：防止路径遍历
            String safeName = objectKey.replace("..", "").replace("/", "_");
            Path target = dir.resolve(safeName);

            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("File saved locally: {}", target);

            return buildUrl(objectKey);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败（本地存储）: " + e.getMessage(), e);
        }
    }

    public String buildUrl(String objectKey) {
        String safeName = objectKey.replace("..", "").replace("/", "_");
        return "/local-uploads/" + safeName;
    }
}
