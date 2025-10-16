# File Tracking Implementation Guide

## Overview
This implementation adds complete file traceability to track which files from original folder paths end up in which final zip files, including handling extracted archives.

## Database Setup

### 1. Run the SQL Script
Execute the SQL script to create the tracking table:
```bash
mysql -u [username] -p [database] < src/main/resources/db/migration/create_zip_tracking_table.sql
```

## Code Changes Required

Due to the large number of method signatures that need updating, here are the remaining methods that need the `FileTrackingContext` and `String sourceZipName` parameters added:

### Methods to Update:

1. **extractZip** - Add parameters: `FileTrackingContext trackingContext, String sourceZipName`
2. **extractTar** - Add parameters: `FileTrackingContext trackingContext, String sourceZipName`
3. **extractTz** - Add parameters: `FileTrackingContext trackingContext, String sourceZipName`
4. **extract7z** - Add parameters: `FileTrackingContext trackingContext, String sourceZipName`
5. **writeToFile** - Add parameter: `FileTrackingContext trackingContext, String sourceZipName`
6. **getUniqueFileName** - Add parameter: `FileTrackingContext trackingContext`
7. **moveToErrorFolder** - Add parameter: `FileTrackingContext trackingContext`
8. **zipFiles** - Add parameter: `FileTrackingContext trackingContext` and implement tracking logic

### Critical Implementation: zipFiles Method

The `zipFiles` method needs significant updates to:
1. Track which files go into each zip
2. Collect file metadata from FileTrackingContext
3. Save tracking data to database after creating each zip

Here's the updated zipFiles method implementation:

```java
private void zipFiles(File tempFolder, String mainFolderPath, String dataSource, FileTrackingContext trackingContext) throws IOException, InterruptedException {
    int currentZipSize = 0;
    ZipOutputStream zos = null;
    File currentZipFile = null;
    List<String> currentZipFileNames = new ArrayList<>();

    FileTransferSettingEntity fileTransferSettingEntity = customMapper.getFileTransferSettingByType(FileTransferSettingEntity.Type.MAX_ZIP_SIZE.name());
    int MAX_ZIP_SIZE = 1024 * 1024; // Default to 1MB
    if(fileTransferSettingEntity != null) {
        try {
            MAX_ZIP_SIZE = Integer.parseInt(fileTransferSettingEntity.getVALUE()) * 1024 * 1024;
        } catch (NumberFormatException e) {
            log.warn("Invalid MAX_ZIP_SIZE value in database, using default: {}", e.getMessage());
        }
    }

    try {
        logWriter.writeLog("Start zipping files in temp folder",
                dataSource,
                mainFolderPath + dataSource + "\\log\\"
        );

        for (File file : Objects.requireNonNull(tempFolder.listFiles())) {
            // If adding the next file will exceed the size limit, close the current zip and start a new one
            if (zos == null || currentZipSize + file.length() > MAX_ZIP_SIZE) {
                // Complete the previous zip record if exists
                if (zos != null) {
                    zos.close();
                    Thread.sleep(1000); // Sleep for 1 second to ensure the zip file is closed properly

                    // Save tracking information for the completed zip
                    saveZipTrackingInfo(currentZipFile, currentZipFileNames, trackingContext, dataSource, mainFolderPath);
                    currentZipFileNames.clear();
                }

                // Create new zip file
                String zipFileName = dataSource + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")) + ".zip";
                currentZipFile = new File(tempFolder.getAbsolutePath() + "\\" + zipFileName);
                zos = new ZipOutputStream(Files.newOutputStream(currentZipFile.toPath()));
                currentZipSize = 0;
            }

            // Add the file to the current zip
            addFileToZip(zos, file);
            currentZipFileNames.add(file.getName());
            currentZipSize += (int) file.length();

            // delete the original file after zipping
            if (!file.delete()) {
                log.warn("Failed to delete file after zipping: {}", file.getAbsolutePath());
            }
        }

        // Save tracking info for the last zip
        if (zos != null && currentZipFile != null && !currentZipFileNames.isEmpty()) {
            saveZipTrackingInfo(currentZipFile, currentZipFileNames, trackingContext, dataSource, mainFolderPath);
        }
    } finally {
        if (zos != null) {
            zos.close();
        }
    }
}

/**
 * Saves tracking information for a completed zip file to the database.
 */
private void saveZipTrackingInfo(File zipFile, List<String> fileNames, FileTrackingContext trackingContext,
                                 String dataSource, String mainFolderPath) {
    try {
        // Create tracking builder
        ZipTrackingService.ZipTrackingDTOBuilder builder = zipTrackingService.createTrackingBuilder(
                zipFile.getName(),
                dataSource,
                trackingContext.getEnvironment()
        );

        // Add all source folder paths
        for (String folderPath : trackingContext.getAllSourceFolderPaths()) {
            builder.addSourceFolderPath(folderPath);
        }

        // Add file information for each file in the zip
        for (String fileName : fileNames) {
            FileInfoDTO fileInfo = trackingContext.getFileInfo(fileName);
            if (fileInfo != null) {
                builder.addFileInfo(fileInfo);
            }
        }

        // Set zip metadata
        builder.setZipSize(zipFile.length());

        // Build and save
        ZipTrackingDTO trackingDTO = builder.build();
        zipTrackingService.saveZipTracking(trackingDTO);

        log.info("Saved tracking info for zip: {} with {} files", zipFile.getName(), fileNames.size());
    } catch (Exception e) {
        log.error("Failed to save tracking info for zip: {}", zipFile.getName(), e);
        // Don't throw exception - tracking failure shouldn't stop the main process
    }
}
```

### For extractZip method, add tracking:
```java
private void extractZip(File zipFile, File destDir, String mainFolderPath, String dataSource, String environment, File tempFolder, FileTrackingContext trackingContext, String sourceZipName) throws IOException {
    try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile.toPath()))) {
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            File entryFile = new File(destDir, entry.getName());
            if (entry.isDirectory()) {
                entryFile.mkdirs();
            } else {
                writeToFile(zipInputStream, entryFile, dataSource, mainFolderPath, environment, trackingContext, sourceZipName);
            }
            zipInputStream.closeEntry();
            if (isCompressedFile(entryFile)) {
                extractCompressedFile(entryFile, mainFolderPath, dataSource, environment, tempFolder, trackingContext);
            }
        }
    }
}
```

### For writeToFile method, add tracking:
```java
private void writeToFile(InputStream inputStream, File file, String dataSource, String mainFolderPath, String environment, FileTrackingContext trackingContext, String sourceZipName) throws IOException {
    boolean isExist = file.exists();

    // Rename the file if it already exists
    file = getUniqueFileName(file, dataSource, mainFolderPath, environment, trackingContext);

    try (FileOutputStream fos = new FileOutputStream(file)) {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
        }
    }

    // Track the extracted file
    trackingContext.trackExtractedFile(file.getName(), sourceZipName, file.length());

    if(isExist) {
        moveToErrorFolder(mainFolderPath, dataSource, file, environment, ErrorType.DUPLICATE_FILE, trackingContext);
    }
}
```

### For moveToErrorFolder, add tracking to remove files from context:
```java
private void moveToErrorFolder(String mainFolderPath, String dataSource, File file, String environment, ErrorType errorType, FileTrackingContext trackingContext) {
    try {
        String errorFolderPath = mainFolderPath + dataSource + "\\error\\files\\" + environment + "\\";
        File errorFolder = new File(errorFolderPath);

        // Create the error folder if it doesn't exist
        if (!errorFolder.exists()) {
            errorFolder.mkdirs();
        }

        File targetFile = new File(errorFolder, file.getName());

        // Rename the file if it already exists
        targetFile = getUniqueFileNameRunningNumber(targetFile, dataSource, environment, errorType);

        if (file.isDirectory()) {
            // Move the directory to the error folder
            FileUtils.moveDirectoryToDirectory(file, targetFile.getParentFile(), true);
        } else {
            // Move the file to the error folder
            FileUtils.moveFile(file, targetFile);
        }

        // Remove from tracking since it's an error file
        trackingContext.removeFile(file.getName());

    } catch (IOException ex) {
        log.error("Failed to move file to error folder: {}", file.getName(), ex);
    }
}
```

## Testing

1. Place test files in your configured folder paths
2. Include some zip files in the test data
3. Run the file processing
4. Query the database:

```sql
SELECT
    FINAL_ZIP_NAME,
    DATA_SOURCE,
    ENVIRONMENT,
    TOTAL_FILES_COUNT,
    JSON_PRETTY(SOURCE_FOLDER_PATHS) as source_folders,
    JSON_PRETTY(FILES_INFO) as files_detail,
    CREATED_TIMESTAMP
FROM file_transfer_zip_tracking
ORDER BY CREATED_TIMESTAMP DESC
LIMIT 10;
```

## Example Output
```json
{
  "final_zip_name": "TEST_DS_20251014T103045.zip",
  "source_folder_paths": [
    "/source/folder1",
    "/source/folder2"
  ],
  "files_info": [
    {
      "file_name": "document.xml",
      "source": "direct",
      "file_size_bytes": 1024,
      "original_folder_path": "/source/folder1"
    },
    {
      "file_name": "data.xml",
      "source": "extracted",
      "original_zip": "archive.zip",
      "file_size_bytes": 2048,
      "original_folder_path": "/source/folder2"
    }
  ]
}
```

## Additional Configuration

Add Gson bean to your Spring configuration if not already present:

```java
@Bean
public Gson gson() {
    return new GsonBuilder()
            .setPrettyPrinting()
            .create();
}
```

## Notes
- The tracking context is created per environment
- Files moved to error folder are removed from tracking
- Database insertions are wrapped in try-catch to not disrupt main flow
- JSON columns provide flexible querying and reporting capabilities
