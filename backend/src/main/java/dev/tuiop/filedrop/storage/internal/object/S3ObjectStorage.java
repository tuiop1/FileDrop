package dev.tuiop.filedrop.storage.internal.object;

import dev.tuiop.filedrop.storage.ObjectStorage;
import dev.tuiop.filedrop.storage.internal.object.exception.ObjectStorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    @Override
    public void store(String objectKey, InputStream content, long contentLength) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(objectKey)
                    .contentType("application/octet-stream")
                    .build();

            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(content, contentLength)
            );

            log.debug(
                    "Stored object bucket={} objectKey={} size={}",
                    s3Properties.bucket(),
                    objectKey,
                    contentLength
            );
        } catch (SdkException exception) {
            throw new ObjectStorageException(
                    "Failed to store object '%s'.".formatted(objectKey),
                    exception
            );
        }
    }

    @Override
    public InputStream load(String objectKey) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(objectKey)
                    .build();

            InputStream content = s3Client.getObject(request);
            log.debug(
                    "Opened object stream bucket={} objectKey={}",
                    s3Properties.bucket(),
                    objectKey
            );
            return content;
        } catch (SdkException exception) {
            throw new ObjectStorageException(
                    "Failed to load object '%s'.".formatted(objectKey),
                    exception
            );
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(request);

            log.debug(
                    "Deleted object bucket={} objectKey={}",
                    s3Properties.bucket(),
                    objectKey
            );
        } catch (SdkException exception) {
            throw new ObjectStorageException(
                    "Failed to delete object '%s'.".formatted(objectKey),
                    exception
            );
        }
    }
}
