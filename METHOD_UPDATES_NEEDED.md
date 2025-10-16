# Remaining Method Signature Updates for FileProcessorService.java

These methods have recursive calls and need their signatures updated to include tracking parameters:

## 1. extractZip Method (around line 553)

**FIND:**
```java
private void extractZip(File zipFile, File destDir, String mainFolderPath, String dataSource, String environment, File tempFolder) throws IOException {
```

**REPLACE WITH:**
```java
private void extractZip(File zipFile, File destDir, String mainFolderPath, String dataSource, String environment, File tempFolder, FileTrackingContext trackingContext, String sourceZipName) throws IOException {
```

**In method body, UPDATE the call to writeToFile:**
```java
writeToFile(zipInputStream, entryFile, dataSource, mainFolderPath, environment, trackingContext, sourceZipName);
```

**In method body, UPDATE recursive extractCompressedFile call:**
```java
extractCompressedFile(entryFile, mainFolderPath, dataSource, environment, tempFolder, trackingContext);
```

## 2. writeToFile Method (around line 569)

**FIND:**
```java
private void writeToFile(InputStream inputStream, File file, String dataSource, String mainFolderPath, String environment) throws IOException {
```

**REPLACE WITH:**
```java
private void writeToFile(InputStream inputStream, File file, String dataSource, String mainFolderPath, String environment, FileTrackingContext trackingContext, String sourceZipName) throws IOException {
```

**In method body, UPDATE getUniqueFileName call:**
```java
file = getUniqueFileName(file, dataSource, mainFolderPath, environment, trackingContext);
```

**ADD tracking after file is written (after the try-with-resources block for FileOutputStream):**
```java
// Track the extracted file
trackingContext.trackExtractedFile(file.getName(), sourceZipName, file.length());
```

**UPDATE moveToErrorFolder call:**
```java
moveToErrorFolder(mainFolderPath, dataSource, file, environment, ErrorType.DUPLICATE_FILE, trackingContext);
```

## 3. extract7z Method (around line 588)

**FIND:**
```java
private void extract7z(File sevenZipFile, File destDir, String mainFolderPath, String dataSource, String environment, File tempFolder) throws IOException {
```

**REPLACE WITH:**
```java
private void extract7z(File sevenZipFile, File destDir, String mainFolderPath, String dataSource, String environment, File tempFolder, FileTrackingContext trackingContext, String sourceZipName) throws IOException {
```

**In method body, UPDATE getUniqueFileName call:**
```java
outputFile = getUniqueFileName(outputFile, dataSource, mainFolderPath, environment, trackingContext);
```

**ADD tracking after file is written (in the else block after FileOutputStream):**
```java
// Track the extracted file
trackingContext.trackExtractedFile(outputFile.getName(), sourceZipName, outputFile.length());
```

**UPDATE moveToErrorFolder call:**
```java
moveToErrorFolder(mainFolderPath, dataSource, outputFile, environment, ErrorType.DUPLICATE_FILE, trackingContext);
```

**UPDATE recursive extractCompressedFile call:**
```java
extractCompressedFile(outputFile, mainFolderPath, dataSource, environment, tempFolder, trackingContext);
```

## 4. getUniqueFileName Method (around line 610)

**FIND:**
```java
private File getUniqueFileName(File file, String dataSource, String mainFolderPath, String environment) {
```

**REPLACE WITH:**
```java
private File getUniqueFileName(File file, String dataSource, String mainFolderPath, String environment, FileTrackingContext trackingContext) {
```

**UPDATE moveToErrorFolder call in the method:**
```java
moveToErrorFolder(mainFolderPath, dataSource, file, environment, ErrorType.DUPLICATE_FILE, trackingContext);
```

## 5. extractTar Method (around line 640)

**FIND:**
```java
private void extractTar(File tarFile, File destDir, String mainFolderPath, String dataSource, String environment, File tempFolder) throws IOException {
```

**REPLACE WITH:**
```java
private void extractTar(File tarFile, File destDir, String mainFolderPath, String dataSource, String environment, File tempFolder, FileTrackingContext trackingContext, String sourceZipName) throws IOException {
```

**In method body, UPDATE writeToFile call:**
```java
writeToFile(tarInputStream, entryFile, dataSource, mainFolderPath, environment, trackingContext, sourceZipName);
```

**UPDATE recursive extractCompressedFile call:**
```java
extractCompressedFile(entryFile, mainFolderPath, dataSource, environment, tempFolder, trackingContext);
```

## 6. extractTz Method (around line 657)

**FIND:**
```java
private void extractTz(File tzFile, File destDir, String mainFolderPath, String dataSource, String environment, File tempFolder) throws IOException {
```

**REPLACE WITH:**
```java
private void extractTz(File tzFile, File destDir, String mainFolderPath, String dataSource, String environment, File tempFolder, FileTrackingContext trackingContext, String sourceZipName) throws IOException {
```

**In method body, UPDATE writeToFile call:**
```java
writeToFile(gzipInputStream, tarFile, dataSource, mainFolderPath, environment, trackingContext, sourceZipName);
```

**UPDATE extractTar call:**
```java
extractTar(tarFile, destDir, mainFolderPath, dataSource, environment, tempFolder, trackingContext, sourceZipName);
```

## 7. zipFiles Method (around line 673) - COMPLETE REPLACEMENT

**REPLACE THE ENTIRE METHOD with:**

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
```

## 8. ADD NEW METHOD saveZipTrackingInfo (add after zipFiles method)

```java
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

## 9. moveToErrorFolder Method (around line 769)

**FIND:**
```java
private void moveToErrorFolder(String mainFolderPath, String dataSource, File file, String environment, ErrorType errorType) {
```

**REPLACE WITH:**
```java
private void moveToErrorFolder(String mainFolderPath, String dataSource, File file, String environment, ErrorType errorType, FileTrackingContext trackingContext) {
```

**ADD at the end of the method (before the catch block):**
```java
        // Remove from tracking since it's an error file
        if (trackingContext != null) {
            trackingContext.removeFile(file.getName());
        }
```

## Summary

All these changes ensure that:
1. File tracking context flows through all methods
2. Direct files are tracked when moved to temp
3. Extracted files are tracked with their source zip name
4. Final zip files get complete tracking records saved to database
5. Error files are removed from tracking

After making these changes, compile and test the application.
