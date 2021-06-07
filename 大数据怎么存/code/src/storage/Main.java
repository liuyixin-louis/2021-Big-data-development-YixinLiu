package storage;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class Main {

    private static String localDirPath;
    private static String bucketName;
    private static AmazonS3 s3;
    private static long partSize = 5 << 20;
    private static boolean uploadAll;
    private static boolean downloadAll;
    private static long bigFileSize = 1024 * 1024 * 20;

    // fsutil file createnew bigfile 15728640
    public static void main(String[] args) throws IOException {

        localDirPath = args[0];
        bucketName = args[1];
        s3 = S3Client.build();

        if (localDirPath == null || bucketName == null){
            throw new IllegalArgumentException("localDir or bucketName is null");
        }

        // 开始同步S3的bucket
        sync();

        // 同步完成，开始监听本地文件夹
        FileMonitor monitor = new FileMonitor(
                localDirPath,
                new FileEventHandler(s3, localDirPath, bucketName)
        );
        monitor.start();
    }

    public static void sync() throws IOException {
        List<S3ObjectSummary> objectSummaries = s3.listObjects(bucketName).getObjectSummaries();
        Set<String> existFileName = new HashSet<>();
        for (S3ObjectSummary summary : objectSummaries){
            String key = summary.getKey();
            boolean bigFile = false;
            if (summary.getSize() > bigFileSize){
                bigFile = true;
            }
            doSync(key, bigFile, summary);
            key = key.replace("/", "\\");
            existFileName.add(key);
        }
        deleteLocalFile(existFileName);
    }


    public static void doSync(String key, boolean bigFile, S3ObjectSummary summary) throws IOException {
        String s = Paths.get(localDirPath, key).toString();
        System.out.format("key: %s\n",s);
        File file = new File(s);
        if(!file.exists()){
            if (key.endsWith("/")){
                boolean result = file.mkdir();
                if (!result){
                    throw new IOException("create file or dictionary " + key + " fail");
                }
            }else {
                downloadFile(key,bigFile);
            }
        } else{
            if (key.endsWith("/"))
                return;

            if (uploadAll){
                uploadFile(file,bucketName, key,summary);
                return;
            }

            if (downloadAll){
                downloadFile(key,bigFile);
                return;
            }

            System.out.format("本地文件与远程文件出现冲突:%s,请确认是否要替换成S3上的文件\n",s);
            Scanner scanner = new Scanner(System.in);
            for(;;){
                String answer = scanner.nextLine();
                if ("ny".equals(answer)){
                    downloadFile(key,bigFile);
                    downloadAll = true;
                    break;
                }else if ("nn".equals(answer)){
                    uploadFile(file,bucketName, key,summary);
                    uploadAll = true;
                    break;
                }
                else if ("y".equals(answer) || "yes".equals(answer)){
                    downloadFile(key,bigFile);
                    break;
                }else if("n".equals(answer) || "no".equals(answer)){
                    uploadFile(file,bucketName, key,summary);
                    break;
                }else {
                    System.out.println("请输入y或n");
                }
            }
        }
    }

    private static void uploadFile(File file, String bucketName,String key, S3ObjectSummary summary){
        if (file.length() < bigFileSize){
            s3.putObject(bucketName, key, file);
        }else {
            uploadBigFile(file,bucketName,key, summary);
        }
    }

    private static void uploadBigFile(File file, String bucketName, String keyName,S3ObjectSummary summary){
        ArrayList<PartETag> partETags = new ArrayList<>();
        long contentLength = file.length();
        String uploadId = null;

        try {
            // Step 1: Initialize.
            InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(bucketName, keyName);
            uploadId = s3.initiateMultipartUpload(initRequest).getUploadId();
            System.out.format("Created upload ID was %s\n", uploadId);

            // Step 2: Upload parts.
            long filePosition = summary.getSize();
            if (filePosition == contentLength){
                filePosition = 0L;
            }

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

                filePosition += partSize;
            }

            // Step 3: Complete.
            CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(bucketName, keyName, uploadId, partETags);
            s3.completeMultipartUpload(compRequest);
            System.out.println("Completing upload");

        } catch (Exception e) {
            System.err.println("uploadBigFile " +e.toString());
            System.exit(1);
        }
    }

    private static void downloadFile(String key, boolean bigFile){
        S3ObjectInputStream s3is = null;
        FileOutputStream fos = null;
        String filePath = Paths.get(localDirPath, key).toString();
        try {
            if (bigFile){
                downloadBigFile(key, filePath);
            }else {
                S3Object o = s3.getObject(bucketName, key);
                s3is = o.getObjectContent();
                fos = new FileOutputStream(new File(filePath));
                byte[] read_buf = new byte[64 * 1024];
                int read_len = 0;
                while ((read_len = s3is.read(read_buf)) > 0) {
                    fos.write(read_buf, 0, read_len);
                }
            }
        } catch (AmazonServiceException e) {
            System.err.println("downloadFile " +e.toString());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("downloadFile " +e.getMessage());
            System.exit(1);
        } finally {
            if (s3is != null) try { s3is.close(); } catch (IOException e) { e.printStackTrace(); }
            if (fos != null) try { fos.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private static void downloadBigFile(String keyName, String filePath){
        File file = new File(filePath);
        S3Object o;

        boolean fileIsExist = file.exists();

        S3ObjectInputStream s3is = null;
        FileOutputStream fos = null;

        try {
            // Step 1: Initialize.
            ObjectMetadata oMetaData = s3.getObjectMetadata(bucketName, keyName);
            final long contentLength = oMetaData.getContentLength();
            final GetObjectRequest downloadRequest =
                    new GetObjectRequest(bucketName, keyName);

            fos = new FileOutputStream(file, true);

            // Step 2: Download parts.
            long filePosition = 0;

            if (fileIsExist && file.length() != contentLength){
                filePosition = file.length();
            }

            for (int i = 1; filePosition < contentLength; i++) {
                // Last part can be less than 5 MB. Adjust part size.
                partSize = Math.min(partSize, contentLength - filePosition);

                // Create request to download a part.
                downloadRequest.setRange(filePosition, filePosition + partSize);
                o = s3.getObject(downloadRequest);

                // download part and save to local file.
                System.out.format("Downloading part %d\n", i);

                filePosition += partSize+1;
                s3is = o.getObjectContent();
                byte[] read_buf = new byte[64 * 1024];
                int read_len = 0;
                while ((read_len = s3is.read(read_buf)) > 0) {
                    fos.write(read_buf, 0, read_len);
                }
            }

            // Step 3: Complete.
            System.out.println("Completing download");
            System.out.format("save %s to %s\n", keyName, filePath);
        } catch (Exception e) {
            System.err.println("downloadBigFile " + e.toString());
            System.exit(1);
        } finally {
            if (s3is != null) try { s3is.close(); } catch (IOException e) { }
            if (fos != null) try { fos.close(); } catch (IOException e) { }
        }
    }

    public static void deleteLocalFile(Set<String> existFileName){
        File[] files = new File(localDirPath).listFiles();
        if (files != null){
            for (File file : files){
                processFile(file, existFileName);
            }
        }
    }

    private static void processFile(File file,Set<String> set) {
        String absolutePath = file.getAbsolutePath();
        int i = absolutePath.indexOf(Paths.get(localDirPath).toString());
        i += localDirPath.length();
        String path = absolutePath.substring(i);


        if (file.isDirectory()) {//如果是目录
            // 如果S3中不含该目录，则删除目录
            if (!set.contains(path + "\\")){
                boolean delete = deleteFile(file);
                if (!delete){
                    System.err.println("删除失败" + path);
                }
                return;
            }
            // 否则继续检查
            File[] listFiles = file.listFiles();//获取当前路径下的所有文件和目录,返回File对象数组
            if (listFiles != null) {
                for (File f : listFiles) {//将目录内的内容对象化并遍历
                    processFile(f, set);
                }
            }
        }else {
            if (!set.contains(path)){
                boolean delete = file.delete();
                if (!delete){
                    System.err.println("删除失败" + path);
                }
            }
        }
    }

    public static boolean deleteFile(File file) {
        boolean success = false;
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File file1 : files) {//递归删除文件或目录
                        deleteFile(file1);
                    }
                }
            }
            success = file.delete();
        }
        return success;
    }
}
