package storage;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FileEventHandler extends FileAlterationListenerAdaptor {

    private AmazonS3 s3;
    private String localDirPath;
    private String bucketName;
    private static long partSize = 5 << 20;
    private static long bigFileSize = 1024 * 1024 * 10;

    public FileEventHandler(AmazonS3 s3, String localDirPath, String bucketName){
        super();
        this.s3 = s3;
        this.localDirPath = localDirPath;
        this.bucketName = bucketName;
    }

    private String convertFilePath(File file){
        String absolutePath = file.getAbsolutePath();
        int i = absolutePath.indexOf(Paths.get(localDirPath).toString());
        i += localDirPath.length();
        String path = absolutePath.substring(i);
        path = path.replace("\\","/");
        return path;
    }

    private void deleteFile(File file, boolean isDir){
        String path = convertFilePath(file);
        if (isDir){
            path += "/";
            recursiveDeleteS3Dictionary(path);
        }else {
            s3.deleteObject(bucketName, path);
        }
    }

    private void recursiveDeleteS3Dictionary(String path){
        try {
            ObjectListing objectListing = s3.listObjects(path);
            if (objectListing != null){
                List<S3ObjectSummary> objectSummaries = objectListing.getObjectSummaries();
                for (S3ObjectSummary summary : objectSummaries){
                    String key = summary.getKey();
                    if (key.endsWith("/")){
                        recursiveDeleteS3Dictionary(path + key);
                    }
                    s3.deleteObject(bucketName, path + key);
                }
            }
        }catch (AmazonServiceException e){
            if (!e.getMessage().contains("null")){
                e.printStackTrace();
            }
        } finally{
            s3.deleteObject(bucketName, path);
        }
    }

    private void updateFile(File file){

        String path = convertFilePath(file);

        if (file.isDirectory()){
            path += "/";
        }

        for (int index = 0; index < 2; index++) {
            try {
                if (file.isDirectory()){
                    ByteArrayInputStream local = new ByteArrayInputStream("".getBytes());
                    ObjectMetadata metadata = new ObjectMetadata();
                    metadata.setContentLength(0);
                    s3.putObject(bucketName,path,local,metadata);
                }else if (file.isFile() && file.length() > bigFileSize){
                    uploadBigFile(file, bucketName, path);
                }
                else{
                    s3.putObject(bucketName, path, file);
                }
                break;
            } catch (AmazonServiceException e) {
                if (e.getErrorCode().equalsIgnoreCase("NoSuchBucket")) {
                    s3.createBucket(bucketName);
                    continue;
                }

                System.err.println("updateFile " +e.toString());
                System.exit(1);
            } catch (AmazonClientException e) {
                try {
                    // detect bucket whether exists
                    s3.getBucketAcl(bucketName);
                } catch (AmazonServiceException ase) {
                    if (ase.getErrorCode().equalsIgnoreCase("NoSuchBucket")) {
                        s3.createBucket(bucketName);
                        continue;
                    }
                } catch (Exception ignore) {
                }

                System.err.println("updateFile " + e.toString());
                System.exit(1);
            }
        }
    }

    private void uploadBigFile(File file, String bucketName, String keyName){
        ArrayList<PartETag> partETags = new ArrayList<>();
        long contentLength = file.length();
        String uploadId = null;

        try {
            // Step 1: Initialize.
            InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(bucketName, keyName);
            uploadId = s3.initiateMultipartUpload(initRequest).getUploadId();
            System.out.format("Created upload ID was %s\n", uploadId);

            // Step 2: Upload parts.
            long filePosition = 0;
            for (int i = 1; filePosition < contentLength; i++) {
                // Last part can be less than 5 MB. Adjust part size.
                partSize = Math.min(partSize, contentLength - filePosition);

                // Create request to upload a part.
                UploadPartRequest uploadRequest = new UploadPartRequest()
                        .withBucketName(bucketName)
                        .withKey(keyName)
                        .withUploadId(uploadId)
                        .withPartNumber(i)
                        .withFileOffset(filePosition)
                        .withFile(file)
                        .withPartSize(partSize);

                // Upload part and add response to our list.
                System.out.format("Uploading part %d\n", i);
                partETags.add(s3.uploadPart(uploadRequest).getPartETag());

                filePosition += partSize + 1;
            }

            // Step 3: Complete.
            System.out.println("Completing upload");
            CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(bucketName, keyName, uploadId, partETags);
            s3.completeMultipartUpload(compRequest);

        } catch (Exception e) {
            System.err.println("uploadBigFile " +e.toString());
            if (uploadId != null && !uploadId.isEmpty()) {
                // Cancel when error occurred
                System.out.println("Aborting upload");
                s3.abortMultipartUpload(new AbortMultipartUploadRequest(bucketName, keyName, uploadId));
            }
            System.exit(1);
        }
    }

    @Override
    public void onFileCreate(File file) {
        System.out.println("onFileCreate.............");
        updateFile(file);
        super.onFileCreate(file);
    }

    @Override
    public void onFileDelete(File file) {
        System.out.println("onFileDelete.............");
        deleteFile(file,false);
        super.onFileDelete(file);
    }

    @Override
    public void onFileChange(File file) {
        System.out.println("change.............");
        updateFile(file);
        super.onFileChange(file);
    }

    @Override
    public void onDirectoryCreate(File directory) {
        System.out.println("dir create.........");
        updateFile(directory);
        super.onDirectoryCreate(directory);
    }

    @Override
    public void onDirectoryDelete(File directory) {
        System.out.println("dir delete.........");
        deleteFile(directory,true);
        super.onDirectoryCreate(directory);
    }
}
