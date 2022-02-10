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
}