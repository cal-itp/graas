package gtfu.tools;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import gtfu.Debug;

public class GCloudBucket {
  private static final String PROJECT_ID = System.getenv("GCP_PROJECT_ID");

  public static void uploadObject(String bucketName, String directory, String fileName, byte[] image) throws IOException {

    String path = directory + "/" + fileName;
    Storage storage = StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();
    BlobId blobId = BlobId.of(bucketName, path);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
    storage.create(blobInfo, image);

    Debug.log("File uploaded to bucket " + bucketName + " as " + path);

  }
}