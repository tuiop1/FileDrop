package dev.tuiop.filedrop.storage.internal.object;

import dev.tuiop.filedrop.storage.ObjectStorage;
import dev.tuiop.filedrop.storage.internal.object.exception.ObjectStorageException;
import lombok.RequiredArgsConstructor;
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

            return s3Client.getObject(request);
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
        } catch (SdkException exception) {
            throw new ObjectStorageException(
                    "Failed to delete object '%s'.".formatted(objectKey),
                    exception
            );
        }
    }
}
