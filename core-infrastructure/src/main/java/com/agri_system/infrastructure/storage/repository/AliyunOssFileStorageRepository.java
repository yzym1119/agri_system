package com.agri_system.infrastructure.storage.repository;

import com.agri_system.domain.storage.FileStorageRepository;
import com.agri_system.infrastructure.storage.config.AliyunOssProperties;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class AliyunOssFileStorageRepository implements FileStorageRepository {

    private final OSS ossClient;
    private final AliyunOssProperties properties;

    @Override
    public String upload(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contentLength);
        if (contentType != null && !contentType.isBlank()) {
            metadata.setContentType(contentType);
        }
        PutObjectRequest request = new PutObjectRequest(properties.getBucketName(), objectKey, inputStream, metadata);
        ossClient.putObject(request);
        return buildUrl(objectKey);
    }

    private String buildUrl(String objectKey) {
        String endpointHost = properties.getEndpoint().replaceFirst("^https?://", "");
        return String.format("https://%s.%s/%s", properties.getBucketName(), endpointHost, objectKey);
    }
}

