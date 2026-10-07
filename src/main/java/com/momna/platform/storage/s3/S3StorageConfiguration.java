package com.momna.platform.storage.s3;

import com.momna.platform.storage.*;
import com.momna.platform.storage.infrastructure.ObjectMetadataRepository;
import java.net.URI;
import java.util.LinkedHashSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "momna.object-storage.enabled", havingValue = "true")
public class S3StorageConfiguration {
    @Bean
    S3Client s3Client(
        @Value("${momna.object-storage.endpoint}") String endpoint,
        @Value("${momna.object-storage.region}") String region,
        @Value("${momna.object-storage.access-key-id}") String accessKey,
        @Value("${momna.object-storage.secret-access-key}") String secretKey
    ) {
        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            ))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }

    @Bean
    S3Presigner s3Presigner(
        @Value("${momna.object-storage.endpoint}") String endpoint,
        @Value("${momna.object-storage.region}") String region,
        @Value("${momna.object-storage.access-key-id}") String accessKey,
        @Value("${momna.object-storage.secret-access-key}") String secretKey
    ) {
        return S3Presigner.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            ))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }

    @Bean
    PrivateObjectStorage privateObjectStorage(
        S3Client s3,
        S3Presigner presigner,
        StorageAuthorization authorization,
        ObjectMetadataRepository metadata,
        @Value("${momna.object-storage.diary-bucket:}") String diaryBucket,
        @Value("${momna.object-storage.medical-bucket:}") String medicalBucket
    ) {
        var buckets = new LinkedHashSet<String>();
        if (!diaryBucket.isBlank()) buckets.add(diaryBucket);
        if (!medicalBucket.isBlank()) buckets.add(medicalBucket);
        return new S3PrivateObjectStorage(s3, presigner, authorization, metadata, buckets);
    }
}
