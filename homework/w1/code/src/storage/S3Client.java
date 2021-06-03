package storage;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

public class S3Client {
    private final static String bucketName = "liuyixi";
    private final static String filePath   = "D:\\pinggao\\hello.txt";
    private final static String accessKey = "346686F194A01571F0F0";
    private final static String secretKey = "WzRDNzIyMDVBM0FFMEUzNDM0QzFDMzU5MTg2RDMw";
    private final static String serviceEndpoint = "http://scut.depts.bingosoft.net:29997";

    private final static String signingRegion = "";

    public static AmazonS3 build() {
        final BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        final ClientConfiguration ccfg = new ClientConfiguration().withUseExpectContinue(false);

        final AwsClientBuilder.EndpointConfiguration endpoint = new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, signingRegion);

        return AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withClientConfiguration(ccfg)
                .withEndpointConfiguration(endpoint)
                .withPathStyleAccessEnabled(true)
                .build();
    }
}
