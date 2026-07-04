package com.heihu.workorder.service;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.heihu.workorder.exception.ApiException;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "app.upload.storage-type", havingValue = "s3")
public class S3ReportImageStorage implements ReportImageStorage {

    private final AmazonS3 client;
    private final String bucket;
    private final String keyPrefix;

    public S3ReportImageStorage(
            @Value("${app.upload.s3.endpoint:}") String endpoint,
            @Value("${app.upload.s3.region:us-east-1}") String region,
            @Value("${app.upload.s3.bucket:}") String bucket,
            @Value("${app.upload.s3.access-key:}") String accessKey,
            @Value("${app.upload.s3.secret-key:}") String secretKey,
            @Value("${app.upload.s3.path-style-access:true}") boolean pathStyleAccess,
            @Value("${app.upload.s3.key-prefix:report-images}") String keyPrefix
    ) {
        if (bucket == null || bucket.trim().isEmpty()) {
            throw new IllegalStateException("app.upload.s3.bucket is required when app.upload.storage-type=s3");
        }
        if (accessKey == null || accessKey.trim().isEmpty() || secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalStateException("S3 access key and secret key are required when app.upload.storage-type=s3");
        }
        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
                .withPathStyleAccessEnabled(pathStyleAccess);
        if (endpoint != null && !endpoint.trim().isEmpty()) {
            builder.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region));
        } else {
            builder.withRegion(region);
        }
        this.client = builder.build();
        this.bucket = bucket;
        this.keyPrefix = normalizePrefix(keyPrefix);
    }

    @Override
    public void store(String storedFileName, MultipartFile file) {
        String key = objectKey(storedFileName);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        if (file.getContentType() != null && !file.getContentType().trim().isEmpty()) {
            metadata.setContentType(file.getContentType());
        }
        try {
            client.putObject(bucket, key, file.getInputStream(), metadata);
        } catch (IOException | AmazonServiceException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "图片保存失败");
        }
    }

    @Override
    public Resource load(String storedFileName) {
        try (S3Object object = client.getObject(bucket, objectKey(storedFileName))) {
            byte[] data = StreamUtils.copyToByteArray(object.getObjectContent());
            return new ByteArrayResource(data);
        } catch (AmazonS3Exception ex) {
            if (ex.getStatusCode() == 404) {
                throw new ApiException(HttpStatus.NOT_FOUND, "图片文件不存在");
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "图片加载失败");
        } catch (IOException | AmazonServiceException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "图片加载失败");
        }
    }

    @Override
    public boolean delete(String storedFileName) {
        try {
            client.deleteObject(bucket, objectKey(storedFileName));
            return true;
        } catch (AmazonS3Exception ex) {
            return ex.getStatusCode() == 404;
        } catch (AmazonServiceException ex) {
            return false;
        }
    }

    private String objectKey(String storedFileName) {
        if (storedFileName == null || storedFileName.trim().isEmpty()
                || storedFileName.contains("..")
                || storedFileName.startsWith("/")
                || storedFileName.startsWith("\\")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "图片不存在");
        }
        return keyPrefix.isEmpty() ? storedFileName : keyPrefix + "/" + storedFileName;
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String normalized = prefix.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
