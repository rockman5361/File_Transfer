package org.ft.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.ft.constants.FileConstants;
import org.ft.dto.FileInfoDTO;
import org.ft.dto.ZipTrackingDTO;
import org.ft.entity.FileTransferFolderPathEntity;
import org.ft.entity.FileTransferDataSourceEntity;
import org.ft.entity.FileTransferSettingEntity;
import org.ft.enums.Environment;
import org.ft.enums.ErrorType;
import org.ft.exception.FileProcessingException;
import org.ft.repository.CustomMapper;
import org.ft.util.FileTrackingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Service for processing file transfers, including compression, extraction,
 * and file management operations.
 */
@Service
@Slf4j
public class FileProcessorService {

    @Autowired
    private LogWriterService logWriter;

    @Autowired
    private CustomMapper customMapper;

    @Autowired
    private FileTransferErrorLogService fileTransferErrorLogService;

    @Autowired
    private ZipTrackingService zipTrackingService;

    @Value(value = "${file-transfer.processing-path}")
    private String mainFolderPath;

    @Value(value = "${file-transfer.upload-to-datalake}")
    private boolean uploadToDatalake;

    @Value(value = "${file-transfer.housekeeping-backup-file-by-year}")
    private int housekeepingBackupFileByYear;

    @Value(value = "${file-transfer.housekeeping-log-file-by-month}")
    private int housekeepingLogFileByMonth;

    /**
     * Deletes old backup files based on configured retention period.
     *
     * @param entity the data source entity
     */
    public void deleteOldBackupFiles(FileTransferDataSourceEntity entity) {
        if (entity == null || !StringUtils.hasText(entity.getDATA_SOURCE())) {
            log.warn("Invalid entity provided for deleteOldBackupFiles");
            return;
        }

        setupFolders(entity.getDATA_SOURCE());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FileConstants.FILE_DATETIME_FORMAT);
        LocalDateTime cutoffDate = LocalDateTime.now().minusYears(housekeepingBackupFileByYear);

        for (Environment value : Environment.values()) {
            Path backupFolderPath = Paths.get(mainFolderPath, entity.getDATA_SOURCE(),
                FileConstants.BACKUP_FOLDER, value.name());
            File backupFolder = backupFolderPath.toFile();

            if (backupFolder.exists() && backupFolder.isDirectory()) {
                File[] files = backupFolder.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (!file.isDirectory()) {
                            try {
                                String dateTimePart = file.getName().substring(
                                    file.getName().lastIndexOf("_") + 1,
                                    file.getName().lastIndexOf(".")
                                );
                                LocalDateTime parsedDateTime = LocalDateTime.parse(dateTimePart, formatter);

                                if (parsedDateTime.isBefore(cutoffDate)) {
                                    if (file.delete()) {
                                        log.info("Deleted old backup file: {}", file.getAbsolutePath());
                                    } else {
                                        log.warn("Failed to delete old backup file: {}", file.getAbsolutePath());
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Error processing backup file: {}", file.getAbsolutePath(), e);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Deletes old log files based on configured retention period.
     *
     * @param entity the data source entity
     */
    public void deleteOldLogFiles(FileTransferDataSourceEntity entity) {
        if (entity == null || !StringUtils.hasText(entity.getDATA_SOURCE())) {
            log.warn("Invalid entity provided for deleteOldLogFiles");
            return;
        }

        setupFolders(entity.getDATA_SOURCE());
        Path logFolderPath = Paths.get(mainFolderPath, entity.getDATA_SOURCE(), FileConstants.LOG_FOLDER);
        File logFolder = logFolderPath.toFile();
        LocalDate cutoffDate = LocalDate.now().minusMonths(housekeepingLogFileByMonth);

        if (logFolder.exists() && logFolder.isDirectory()) {
            File[] files = logFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isDirectory()) {
                        try {
                            String dateTimePart = file.getName().substring(
                                file.getName().lastIndexOf("_") + 1,
                                file.getName().lastIndexOf(".")
                            );
                            LocalDate parsedDateTime = LocalDate.parse(dateTimePart);

                            if (parsedDateTime.isBefore(cutoffDate)) {
                                if (file.delete()) {
                                    log.info("Deleted old log file: {}", file.getAbsolutePath());
                                } else {
                                    log.warn("Failed to delete old log file: {}", file.getAbsolutePath());
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error processing log file: {}", file.getAbsolutePath(), e);
                        }
                    }
                }
            }
        }
    }

    /**
     * Processes file transfers for a given data source.
     *
     * @param entity the data source entity
     * @param folderPaths list of folder paths to process
     * @throws FileProcessingException if processing fails
     */
    public void processFileTransferDataSource(FileTransferDataSourceEntity entity, List<FileTransferFolderPathEntity> folderPaths) {
        if (entity == null || !StringUtils.hasText(entity.getDATA_SOURCE())) {
            throw new FileProcessingException("Invalid entity provided for processFileTransferDataSource");
        }

        if (folderPaths == null) {
            log.warn("No folder paths provided for data source: {}", entity.getDATA_SOURCE());
            return;
        }

        try {
            setupFolders(entity.getDATA_SOURCE());

            Path basePath = Paths.get(mainFolderPath, entity.getDATA_SOURCE());
            String successLogPath = Paths.get(basePath.toString(), FileConstants.LOG_FOLDER) + File.separator;
            String errorLogPath = Paths.get(basePath.toString(), FileConstants.ERROR_FOLDER, FileConstants.LOG_FOLDER) + File.separator;
            String tempFolderPath = Paths.get(basePath.toString(), FileConstants.TEMP_FOLDER) + File.separator;

            // Map the folderPaths to same environment
            Map<String, List<FileTransferFolderPathEntity>> folderPathsByEnvironment = folderPaths.stream()
                    .filter(folderPathEntity -> folderPathEntity.getDATA_SOURCE_ID().equals(entity.getID()))
                    .collect(Collectors.groupingBy(FileTransferFolderPathEntity::getENVIRONMENT));

            for (Map.Entry<String, List<FileTransferFolderPathEntity>> entry : folderPathsByEnvironment.entrySet()) {
                String environment = entry.getKey();
                List<FileTransferFolderPathEntity> folderPathEntityList = entry.getValue();

                // Create tracking context for this environment
                FileTrackingContext trackingContext = new FileTrackingContext(environment);

                // Create temp folder for each environment
                Path environmentTempPath = Paths.get(tempFolderPath, environment);
                File environmentTempFolder = environmentTempPath.toFile();
                if (!environmentTempFolder.exists()) {
                    environmentTempFolder.mkdirs();
                }

                // Create backup folder for each environment
                Path environmentBackupPath = Paths.get(mainFolderPath, entity.getDATA_SOURCE(),
                    FileConstants.BACKUP_FOLDER, environment);
                File environmentBackupFolder = environmentBackupPath.toFile();
                if (!environmentBackupFolder.exists()) {
                    environmentBackupFolder.mkdirs();
                }

                // Move each folder path for the current environment
                processFolderPaths(folderPathEntityList, entity, environmentTempFolder, successLogPath, environment, trackingContext);

                // extract compressed files in temp folder
                extractCompressedFiles(environmentTempFolder, entity.getDATA_SOURCE(), successLogPath, errorLogPath, environment, trackingContext);

                // zip files in temp folder
                File[] tempFiles = environmentTempFolder.listFiles();
                if (tempFiles != null && tempFiles.length > 0) {
                    try {
                        zipFiles(environmentTempFolder, entity.getDATA_SOURCE(), trackingContext);

                        logWriter.writeLog("Total Zipfile : " + tempFiles.length,
                                entity.getDATA_SOURCE(),
                                successLogPath
                        );

                        logWriter.writeLog("End of zipping files in temp folder",
                                entity.getDATA_SOURCE(),
                                successLogPath
                        );

                    } catch (IOException | InterruptedException e) {
                        log.error("Failed to zip files in temp folder for data source: {}", entity.getDATA_SOURCE(), e);
                        throw new FileProcessingException("Failed to zip files", e);
                    }
                }

                // send zip file to datalake and move files to backup folder
                tempFiles = environmentTempFolder.listFiles();
                if (tempFiles != null) {
                    for (File file : tempFiles) {
                        if (uploadToDatalake) {
                            try {
                                sendToFileToDatalake(environment, file, entity.getDATA_SOURCE(), successLogPath);
                                // Update datalake upload status in tracking
                                zipTrackingService.updateDatalakeUploadStatus(file.getName(), true);
                            } catch (Exception e) {
                                log.error("Failed to send file to datalake: {}", file.getName(), e);
                            }
                        }
                        moveFileToBackup(file, environment, entity.getDATA_SOURCE(), successLogPath);
                    }
                }
            }

            logWriter.writeLog("------------------------------------------------------------",
                    entity.getDATA_SOURCE(),
                    successLogPath
            );
        } catch (Exception e) {
            log.error("Error processing file transfer for data source: {}", entity.getDATA_SOURCE(), e);
            throw new FileProcessingException("Error processing file transfer", entity.getDATA_SOURCE(), "processFileTransferDataSource", e);
        }
    }

    private void sendToFileToDatalake(String environment, File file, String dataSource, String successLogPath) throws Exception {
        logWriter.writeLog("Starting of E142 file transfer to datalake: " + file.getName(),
                dataSource,
                successLogPath
        );

//        DatalakeInfo datalakeInfo = new DatalakeInfo(environment, Collections.singletonList(file.getAbsolutePath()), dataSource);
//        ConfigInfo configInfo = hdfsFileSystemService.getConfigInfo(environment);
//        transferToDatalakeService.uploadIntoHdfs(datalakeInfo, configInfo);

        logWriter.writeLog("End of E142 file transfer to datalake",
                dataSource,
                successLogPath
        );
    }

    private void processFolderPaths(List<FileTransferFolderPathEntity> folderPaths, FileTransferDataSourceEntity dataSource, File tempFolder, String successLogPath, String environment, FileTrackingContext trackingContext) {
        logWriter.writeLog("Starting of moving files to temp folder",
                dataSource.getDATA_SOURCE(),
                successLogPath
        );

        for (FileTransferFolderPathEntity folderPathEntity : folderPaths) {
            if (!dataSource.getID().equals(folderPathEntity.getDATA_SOURCE_ID())) {
                continue;
            }

            File folderPath = new File(folderPathEntity.getFOLDER_PATH());
            File[] files = folderPath.listFiles();
            if (files == null || files.length == 0) {
                continue;
            }

            logWriter.writeLog("Total file in " + folderPath.getAbsolutePath() + " has : " + Objects.requireNonNull(folderPath.listFiles()).length + " files",
                    dataSource.getDATA_SOURCE(),
                    successLogPath
            );

            for (File file : Objects.requireNonNull(folderPath.listFiles())) {
                moveFileToTemp(file, tempFolder, dataSource.getDATA_SOURCE(), successLogPath, environment, trackingContext, folderPathEntity.getFOLDER_PATH());
            }
        }

        logWriter.writeLog("End of moving files to temp folder",
                dataSource.getDATA_SOURCE(),
                successLogPath
        );
    }

    private void moveFileToTemp(File file, File tempFolder, String dataSource, String successLogPath, String environment, FileTrackingContext trackingContext, String originalFolderPath) {
        logWriter.writeLog("Moved : " + file.getAbsolutePath() + " to " + tempFolder.getAbsolutePath(), dataSource, successLogPath);
        try {
            if(file.isDirectory()) {
                FileUtils.moveDirectoryToDirectory(file, tempFolder, true);
                // Check if the directory is empty before attempting to delete
                File[] files = file.listFiles();
                if (files != null && files.length == 0) {
                    file.delete();
                }
            } else {
                FileUtils.moveFileToDirectory(file, tempFolder, true);
                // Track this file as a direct file from the folder
                trackingContext.trackDirectFile(file.getName(), originalFolderPath, file.length());
            }
        } catch (IOException e) {
            if (e.getMessage().contains("already exists")) {
                // Move both files to the error folder
                fileExists(tempFolder, file, dataSource, environment, trackingContext);
            }
        }
    }

    private void moveFileToBackup(File file, String environment, String dataSource, String successLogPath) {
        logWriter.writeLog("Starting moving files to backup folder",
                dataSource,
                successLogPath
        );

        File backupFolder = new File(mainFolderPath + dataSource + File.separator + FileConstants.BACKUP_FOLDER + File.separator + environment);
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }
        try {
            String fileName = file.getName();
            FileUtils.moveFileToDirectory(file, backupFolder, true);

            // Update the backup path in the tracking record
            File movedFile = new File(backupFolder, fileName);
            zipTrackingService.updateBackupPath(fileName, movedFile.getAbsolutePath());

            logWriter.writeLog("Moved file: " + movedFile.getAbsolutePath() + " to backup folder: " + backupFolder.getAbsolutePath(),
                    dataSource,
                    successLogPath
            );
        } catch (IOException e) {
            log.error("Failed to move file: {} to backup folder", file.getAbsolutePath(), e);
        }

        logWriter.writeLog("End of moving files to backup folder",
                dataSource,
                successLogPath
        );
    }

    private void extractCompressedFiles(File tempFolder, String dataSource, String successLogPath, String errorLogPath, String environment, FileTrackingContext trackingContext) {
        if (tempFolder.listFiles() != null && Objects.requireNonNull(tempFolder.listFiles()).length != 0) {
            logWriter.writeLog("Start Compressed files in temp folder",
                    dataSource,
                    successLogPath
            );
        }

        for (File file : Objects.requireNonNull(tempFolder.listFiles())) {
            if (file.isDirectory()) {
                try {
                    processDirectory(file, dataSource, tempFolder, environment, trackingContext);
                } catch (IOException e) {
                    if (e.getMessage().contains(FileConstants.FILE_ALREADY_EXISTS)) {
                        fileExists(tempFolder, file, dataSource, environment, trackingContext);
                    } else {
                        logWriter.writeLog("Failed to extract compressed file: " + file.getAbsolutePath() + " - " + e.getMessage(),
                                dataSource,
                                errorLogPath
                        );

                        // insert error log
                        fileTransferErrorLogService.insertOrUpdateErrorLog(dataSource, environment, file.getName(), ErrorType.EXTRACTION_ERROR, Collections.singletonList(file.getName()));

                        // Only move to error folder if the file still exists (might have been moved already during processing)
                        if (file.exists()) {
                            moveToErrorFolder(dataSource, file, environment, ErrorType.EXTRACTION_ERROR, trackingContext);
                        }
                    }
                }
            } else if (isCompressedFile(file)) {
                try {
                    extractCompressedFile(file, dataSource, environment, tempFolder, trackingContext);
                } catch (IOException e) {
                    logWriter.writeLog("Failed to extract compressed file: " + file.getAbsolutePath() + " - " + e.getMessage(),
                            dataSource,
                            errorLogPath
                    );

                    // insert error log
                    fileTransferErrorLogService.insertOrUpdateErrorLog(dataSource, environment, file.getName(), ErrorType.EXTRACTION_ERROR, Collections.singletonList(file.getName()));

                    // Only move to error folder if the file still exists (might have been moved already during extraction)
                    if (file.exists()) {
                        moveToErrorFolder(dataSource, file, environment, ErrorType.EXTRACTION_ERROR, trackingContext);
                    }
                }
            }
        }

        for (File file : Objects.requireNonNull(tempFolder.listFiles())) {
            if (file.isDirectory()) {
                try {
                    processDirectory(file, dataSource, tempFolder, environment, trackingContext);
                } catch (IOException e) {
                    log.error("Failed to process directory: {}", file.getAbsolutePath(), e);
                }
            } else if (!file.getName().endsWith(FileConstants.XML_EXTENSION)) {
                logWriter.writeLog("Unsupported file format: " + file.getName(),
                        dataSource,
                        errorLogPath
                );

                // insert error log
                fileTransferErrorLogService.insertOrUpdateErrorLog(dataSource, environment, file.getName(), ErrorType.WRONG_FILE_TYPE, Collections.singletonList(file.getName()));
                moveToErrorFolder(dataSource, file, environment, ErrorType.WRONG_FILE_TYPE, trackingContext);
            }
        }

        if (tempFolder.listFiles() != null && Objects.requireNonNull(tempFolder.listFiles()).length != 0) {
            logWriter.writeLog("End of compressed files in temp folder",
                    dataSource,
                    successLogPath
            );
        }
    }

    private void processDirectory(File directory, String dataSource, File tempFolder, String environment, FileTrackingContext trackingContext) throws IOException {
        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (file.isDirectory()) {
                processDirectory(file, dataSource, tempFolder, environment, trackingContext);
            } else if (isCompressedFile(file)) {
                extractCompressedFile(file, dataSource, environment, tempFolder, trackingContext);
            } else {
                try {
                    FileUtils.moveFileToDirectory(file, tempFolder, true);
                    // Note: Files from directories don't get tracked as they're intermediate
                } catch (IOException e) {
                    if (e.getMessage().contains(FileConstants.FILE_ALREADY_EXISTS)) {
                        // Move both files to the error folder
                        fileExists(tempFolder, file, dataSource, environment, trackingContext);
                    }
                }
            }
        }

        // Remove the directory itself if it's empty after processing
        if (Objects.requireNonNull(directory.listFiles()).length == 0) {
            directory.delete();
        }
    }

    private void fileExists(File tempFolder, File file, String dataSource, String environment, FileTrackingContext trackingContext) {
        if(file.isDirectory() || isCompressedFile(file)) {
            // If the file is a directory, we need to rename it before moving
            try {
                // Create new file name with suffix
                File renamedFile = renameFileName(file, 1);

                if(renamedFile.isDirectory()) {
                    // Move the directory to the error folder
                    FileUtils.moveDirectoryToDirectory(renamedFile, tempFolder, true);
                    File[] files = file.listFiles();
                    if (files != null && files.length == 0) {
                        file.delete();
                    }
                } else {
                    // Move the file to the error folder
                    FileUtils.moveFileToDirectory(renamedFile, tempFolder, true);
                }
            } catch (IOException e) {
                log.error("Failed to rename directory: {}", file.getAbsolutePath(), e);
            }
        } else {
            // Create new file name with suffix
            File renamedFile = renameFileName(file, 1);

            // Move both files to the error folder
            File targetFile = new File(tempFolder, file.getName());

            moveToErrorFolder(dataSource, targetFile, environment, ErrorType.DUPLICATE_FILE, trackingContext);
            moveToErrorFolder(dataSource, renamedFile, environment, ErrorType.DUPLICATE_FILE, trackingContext);

            // insert error log
            List<String> errorFileList = new ArrayList<>();
            errorFileList.add(file.getName());
            errorFileList.add(renamedFile.getName());
            fileTransferErrorLogService.insertOrUpdateErrorLog(dataSource, environment, file.getName(), ErrorType.DUPLICATE_FILE, errorFileList);

            logWriter.writeLog("File already duplicate: " + file.getName(),
                    dataSource,
                    mainFolderPath + dataSource + File.separator + FileConstants.ERROR_FOLDER + File.separator + FileConstants.LOG_FOLDER + File.separator
            );
        }
    }

    private File renameFileName(File file, int counter) {
        // Extract file name and extension
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf(".");
        String baseName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
        String extension = (dotIndex == -1) ? "" : fileName.substring(dotIndex);

        // Create new file name with suffix
        File renamedFile = new File(file.getParent(), baseName + "(" + counter + ")" + extension);
        if (renamedFile.exists()) {
            counter++;
            return renameFileName(renamedFile, counter);
        }

        file.renameTo(renamedFile);
        return renamedFile;
    }

    private void extractCompressedFile(File file, String dataSource, String environment, File tempFolderPath, FileTrackingContext trackingContext) throws IOException {
        logWriter.writeLog("Extracted compressed file: " + file.getAbsolutePath(),
                dataSource,
                mainFolderPath + dataSource + File.separator + FileConstants.LOG_FOLDER + File.separator
        );

        File destDir = file.getParentFile();
        String sourceZipName = file.getName();

        if (file.getName().endsWith(".zip")) {
            extractZip(file, destDir, dataSource, environment, tempFolderPath, trackingContext, sourceZipName);
        } else if (file.getName().endsWith(".tar")) {
            extractTar(file, destDir, dataSource, environment, tempFolderPath, trackingContext, sourceZipName);
        } else if (file.getName().endsWith(".tz") || file.getName().endsWith(".tar.gz")) {
            extractTz(file, destDir, dataSource, environment, tempFolderPath, trackingContext, sourceZipName);
        } else if (file.getName().endsWith(".7z")) {
            extract7z(file, destDir, dataSource, environment, tempFolderPath, trackingContext, sourceZipName);
        }

        if (!file.delete()) {
            log.warn("Failed to delete compressed file: {}", file.getAbsolutePath());
        }
    }

    private void extractZip(File zipFile, File destDir, String dataSource, String environment, File tempFolder, FileTrackingContext trackingContext, String sourceZipName) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile.toPath()))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                File entryFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    writeToFile(zipInputStream, entryFile, dataSource, environment, trackingContext, sourceZipName);
                }
                zipInputStream.closeEntry();
                if (isCompressedFile(entryFile)) {
                    extractCompressedFile(entryFile, dataSource, environment, tempFolder, trackingContext);
                }
            }
        }
    }

    private void writeToFile(InputStream inputStream, File file, String dataSource, String environment, FileTrackingContext trackingContext, String sourceZipName) throws IOException {
        boolean isExist = file.exists();

        // Rename the file if it already exists
        file = getUniqueFileName(file, dataSource, environment, trackingContext);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[FileConstants.BUFFER_SIZE];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }

        // Track the extracted file
        trackingContext.trackExtractedFile(file.getName(), sourceZipName, file.length());

        if(isExist) {
            moveToErrorFolder(dataSource, file, environment, ErrorType.DUPLICATE_FILE, trackingContext);
        }
    }

    private void extract7z(File sevenZipFile, File destDir, String dataSource, String environment, File tempFolder, FileTrackingContext trackingContext, String sourceZipName) throws IOException {
        try (SevenZFile sevenZFile = new SevenZFile(sevenZipFile)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                File outputFile = new File(destDir, entry.getName());
                File parentDir = outputFile.getParentFile();
                parentDir.mkdirs();

                boolean isExist = outputFile.exists();

                // Rename the file if it already exists
                outputFile = getUniqueFileName(outputFile, dataSource, environment, trackingContext);

                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[FileConstants.BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = sevenZFile.read(buffer)) > 0) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }

                    // Track the extracted file
                    trackingContext.trackExtractedFile(outputFile.getName(), sourceZipName, outputFile.length());

                    if(isExist) {
                        moveToErrorFolder(dataSource, outputFile, environment, ErrorType.DUPLICATE_FILE, trackingContext);
                    }
                }

                if (isCompressedFile(outputFile)) {
                    extractCompressedFile(outputFile, dataSource, environment, tempFolder, trackingContext);
                }
            }
        }
    }

    private File getUniqueFileName(File file, String dataSource, String environment, FileTrackingContext trackingContext) {
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf(".");
        String baseName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
        String extension = (dotIndex == -1) ? "" : fileName.substring(dotIndex);

        File uniqueFile = file;
        int counter = 1;
        while (uniqueFile.exists()) {
            uniqueFile = new File(file.getParent(), baseName + "(" + counter + ")" + extension);

            if(!(uniqueFile.isDirectory() && isCompressedFile(uniqueFile))) {
                moveToErrorFolder(dataSource, file, environment, ErrorType.DUPLICATE_FILE, trackingContext);
                logWriter.writeLog("File already duplicate: " + file.getName(),
                        dataSource,
                        mainFolderPath + dataSource + File.separator + FileConstants.ERROR_FOLDER + File.separator + FileConstants.LOG_FOLDER + File.separator
                );

                // insert error log
                List<String> errorFileList = new ArrayList<>();
                errorFileList.add(file.getName());
                errorFileList.add(uniqueFile.getName());
                fileTransferErrorLogService.insertOrUpdateErrorLog(dataSource, environment, file.getName(), ErrorType.DUPLICATE_FILE, errorFileList);
            }

            counter++;
        }
        return uniqueFile;
    }

    private void extractTar(File tarFile, File destDir, String dataSource, String environment, File tempFolder, FileTrackingContext trackingContext, String sourceZipName) throws IOException {
        try (TarArchiveInputStream tarInputStream = new TarArchiveInputStream(Files.newInputStream(tarFile.toPath()))) {
            TarArchiveEntry entry;
            while ((entry = tarInputStream.getNextTarEntry()) != null) {
                File entryFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    writeToFile(tarInputStream, entryFile, dataSource, environment, trackingContext, sourceZipName);
                }
                if (isCompressedFile(entryFile)) {
                    extractCompressedFile(entryFile, dataSource, environment, tempFolder, trackingContext);
                }
            }
        }
    }

    private void extractTz(File tzFile, File destDir, String dataSource, String environment, File tempFolder, FileTrackingContext trackingContext, String sourceZipName) throws IOException {
        try (GzipCompressorInputStream gzipInputStream = new GzipCompressorInputStream(Files.newInputStream(tzFile.toPath()))) {
            File tarFile = new File(destDir, tzFile.getName().replace(FileConstants.GZ_EXTENSION, ""));
            writeToFile(gzipInputStream, tarFile, dataSource, environment, trackingContext, sourceZipName);
            extractTar(tarFile, destDir, dataSource, environment, tempFolder, trackingContext, sourceZipName);
            if (!tarFile.delete()) {
                log.warn("Failed to delete intermediate .tar file: {}", tarFile.getAbsolutePath());
            }
        }
    }

    private boolean isCompressedFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(FileConstants.ZIP_EXTENSION) || name.endsWith(FileConstants.TAR_EXTENSION) || name.endsWith(FileConstants.TZ_EXTENSION) || name.endsWith(FileConstants.TAR_GZ_EXTENSION) || name.endsWith(FileConstants.SEVEN_Z_EXTENSION);
    }

    private void zipFiles(File tempFolder, String dataSource, FileTrackingContext trackingContext) throws IOException, InterruptedException {
        int currentZipSize = 0;
        ZipOutputStream zos = null;
        File currentZipFile = null;
        List<String> currentZipFileNames = new ArrayList<>();

        FileTransferSettingEntity fileTransferSettingEntity = customMapper.getFileTransferSettingByType(FileTransferSettingEntity.Type.MAX_ZIP_SIZE.name());
        int MAX_ZIP_SIZE = FileConstants.DEFAULT_MAX_ZIP_SIZE_MB; // Default to 1MB
        if(fileTransferSettingEntity != null) {
            try {
                MAX_ZIP_SIZE = Integer.parseInt(fileTransferSettingEntity.getVALUE()) * FileConstants.DEFAULT_MAX_ZIP_SIZE_MB;
            } catch (NumberFormatException e) {
                log.warn("Invalid MAX_ZIP_SIZE value in database, using default: {}", e.getMessage());
            }
        }

        try {
            logWriter.writeLog("Start zipping files in temp folder",
                    dataSource,
                    mainFolderPath + dataSource + File.separator + FileConstants.LOG_FOLDER + File.separator
            );

            for (File file : Objects.requireNonNull(tempFolder.listFiles())) {
                // If adding the next file will exceed the size limit, close the current zip and start a new one
                if (zos == null || currentZipSize + file.length() > MAX_ZIP_SIZE) {
                    // Complete the previous zip record if exists
                    if (zos != null) {
                        zos.close();
                        Thread.sleep(FileConstants.ZIP_CLOSE_SLEEP_MS); // Sleep for 1 second to ensure the zip file is closed properly

                        // Save tracking information for the completed zip
                        saveZipTrackingInfo(currentZipFile, currentZipFileNames, trackingContext, dataSource);
                        currentZipFileNames.clear();
                    }

                    // Create new zip file
                    String zipFileName = dataSource + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern(FileConstants.FILE_DATETIME_FORMAT)) + FileConstants.ZIP_EXTENSION;
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
                saveZipTrackingInfo(currentZipFile, currentZipFileNames, trackingContext, dataSource);
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
    private void saveZipTrackingInfo(File zipFile, List<String> fileNames, FileTrackingContext trackingContext, String dataSource) {
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

    private void addFileToZip(ZipOutputStream zos, File file) throws IOException {
        if (!isCompressedFile(file)) {
            try (FileInputStream fis = new FileInputStream(file)) {
                zos.putNextEntry(new ZipEntry(file.getName()));
                byte[] buffer = new byte[FileConstants.BUFFER_SIZE];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                zos.closeEntry();
            }
        }
    }

    private void setupFolders(String dataSource) {
        // Create the data source folder if it doesn't exist
        createFolder(mainFolderPath + dataSource);

        // Create temp folder if it doesn't exist
        createFolder(mainFolderPath + dataSource + File.separator + FileConstants.TEMP_FOLDER);

        // Create backup folder if it doesn't exist
        createFolder(mainFolderPath + dataSource + File.separator + FileConstants.BACKUP_FOLDER);

        // Create log folder if it doesn't exist
        createFolder(mainFolderPath + dataSource + File.separator + FileConstants.LOG_FOLDER);

        // Create error folder if it doesn't exist
        createFolder(mainFolderPath + dataSource + File.separator + FileConstants.ERROR_FOLDER);

        // Create error sub folder if it doesn't exist
        createFolder(mainFolderPath + dataSource + File.separator + FileConstants.ERROR_FOLDER + File.separator + FileConstants.FILES_FOLDER);
        createFolder(mainFolderPath + dataSource + File.separator + FileConstants.ERROR_FOLDER + File.separator + FileConstants.LOG_FOLDER);
    }

    private void createFolder(String path) {
        File file = new File(path);

        // Check if file exists, create a new one if it doesn't
        if (!file.exists()) {
            file.mkdir();
        }
    }

    private void moveToErrorFolder(String dataSource, File file, String environment, ErrorType errorType, FileTrackingContext trackingContext) {
        try {
            String errorFolderPath = mainFolderPath + dataSource + File.separator + FileConstants.ERROR_FOLDER + File.separator + FileConstants.FILES_FOLDER + environment + File.separator;
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
            if (trackingContext != null) {
                trackingContext.removeFile(file.getName());
            }
        } catch (IOException ex) {
            log.error("Failed to move file to error folder: {}", file.getName(), ex);
        }
    }

    private File getUniqueFileNameRunningNumber(File file, String dataSource, String environment, ErrorType errorType) {
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf(".");
        String baseName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
        String extension = (dotIndex == -1) ? "" : fileName.substring(dotIndex);

        int counter = 0;
        // Check if the base name already has a number in parentheses
        int openParenIndex = baseName.lastIndexOf("(");
        int closeParenIndex = baseName.lastIndexOf(")");
        if (openParenIndex != -1 && closeParenIndex != -1 && closeParenIndex > openParenIndex) {
            String numberPart = baseName.substring(openParenIndex + 1, closeParenIndex);
            try {
                counter = Integer.parseInt(numberPart);
                baseName = baseName.substring(0, openParenIndex);
            } catch (NumberFormatException ignored) {
                counter = 0;
            }
        }

        File uniqueFile = file;
        if (uniqueFile.exists()) {
            do {
                counter++;
                uniqueFile = new File(file.getParent(), baseName + "(" + counter + ")" + extension);
            } while (uniqueFile.exists());

            // insert error log
            fileTransferErrorLogService.insertOrUpdateErrorLog(dataSource, environment, baseName + extension, errorType, Collections.singletonList(uniqueFile.getName()));
        }

        return uniqueFile;
    }
}