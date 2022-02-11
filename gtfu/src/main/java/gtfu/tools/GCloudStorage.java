package gtfu.tools;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import gtfu.Debug;

public class GCloudStorage {
  private static final String PROJECT_ID = System.getenv("GCP_PROJECT_ID");

  // Upload without specifying content type will apply GCloud's default content type
  public static void uploadObject(String bucketName, String directory, String fileName, byte[] file) throws IOException {
    uploadObject(bucketName, directory, fileName, file, "application/octet-stream");
  }

  public static void uploadObject(String bucketName, String directory, String fileName, byte[] file, String contentType) throws IOException {
    String path = directory + "/" + fileName;
    Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
    BlobId blobId = BlobId.of(bucketName, path);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(contentType).build();
    storage.create(blobInfo, file);

    Debug.log("File uploaded to bucket " + bucketName + " as " + path);

  }

  private static void usage() {
      System.err.println("usage: GCloudStorage -b <bucket-name> -p <file-path> -n <file-name> [-t <file-type>]");
      System.exit(0);
  }

  public static void main(String[] arg) throws Exception {
    String bucketName = null;
    String directory = null;
    String filePath = null;
    String fileName = null;
    String fileType = null;

    for (int i=0; i<arg.length; i++) {
        if (arg[i].equals("-b") && i < arg.length - 1) {
            bucketName = arg[i + 1];
        }
        if (arg[i].equals("-p") && i < arg.length - 1) {
            filePath = arg[i + 1];
        }
        if (arg[i].equals("-d") && i < arg.length - 1) {
            directory = arg[i + 1];
        }
        if (arg[i].equals("-n") && i < arg.length - 1) {
            fileName = arg[i + 1];
        }
        if (arg[i].equals("-t") && i < arg.length - 1) {
            fileType = arg[i + 1];
        }
    }
    if ((bucketName == null) || (filePath == null) || (fileName == null)){
      usage();
    }
    if (fileType != null){
      uploadObject(bucketName, directory, fileName, Files.readAllBytes(Paths.get(filePath)),fileType);
    }
    else uploadObject(bucketName, directory, fileName, Files.readAllBytes(Paths.get(filePath)));
  }
}