package org.ft.services;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.ft.dto.FileInfoDTO;
import org.ft.dto.ZipTrackingDTO;
import org.ft.entity.FileTransferZipTrackingEntity;
import org.ft.repository.FileTransferZipTrackingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for tracking zip file creation and maintaining file lineage.
 * This service helps trace back which original folder paths contributed to each final zip file.
 */
@Service
@Slf4j
public class ZipTrackingService {

    @Autowired
    private FileTransferZipTrackingRepository zipTrackingRepository;

    @Autowired
    private Gson gson;

    /**
     * Saves zip tracking information to the database.
     *
     * @param trackingDTO the tracking data to save
     * @return the saved entity
     */
    @Transactional
    public FileTransferZipTrackingEntity saveZipTracking(ZipTrackingDTO trackingDTO) {
        try {
            FileTransferZipTrackingEntity entity = new FileTransferZipTrackingEntity();
            entity.setFINAL_ZIP_NAME(trackingDTO.getFinalZipName());
            entity.setDATA_SOURCE(trackingDTO.getDataSource());
            entity.setENVIRONMENT(trackingDTO.getEnvironment());

            // Convert Set to JSON array string
            entity.setSOURCE_FOLDER_PATHS(gson.toJson(trackingDTO.getSourceFolderPaths()));

            // Convert List<FileInfoDTO> to JSON array string
            entity.setFILES_INFO(gson.toJson(trackingDTO.getFilesInfo()));

            entity.setZIP_SIZE_BYTES(trackingDTO.getZipSizeBytes());
            entity.setTOTAL_FILES_COUNT(trackingDTO.getTotalFilesCount());
            entity.setCREATED_TIMESTAMP(trackingDTO.getCreatedTimestamp());
            entity.setBACKUP_PATH(trackingDTO.getBackupPath());
            entity.setUPLOADED_TO_DATALAKE(trackingDTO.getUploadedToDatalake());

            FileTransferZipTrackingEntity savedEntity = zipTrackingRepository.save(entity);

            log.info("Saved zip tracking for: {} with {} files from {} source folders",
                    trackingDTO.getFinalZipName(),
                    trackingDTO.getTotalFilesCount(),
                    trackingDTO.getSourceFolderPaths().size());

            return savedEntity;
        } catch (Exception e) {
            log.error("Failed to save zip tracking for: {}", trackingDTO.getFinalZipName(), e);
            throw e;
        }
    }

    /**
     * Updates the datalake upload status for a zip file.
     *
     * @param zipFileName the name of the zip file
     * @param uploaded whether it was uploaded successfully
     */
    @Transactional
    public void updateDatalakeUploadStatus(String zipFileName, boolean uploaded) {
        try {
            FileTransferZipTrackingEntity entity = zipTrackingRepository.findByFinalZipName(zipFileName);
            if (entity != null) {
                entity.setUPLOADED_TO_DATALAKE(uploaded);
                zipTrackingRepository.save(entity);
                log.info("Updated datalake upload status for: {} to {}", zipFileName, uploaded);
            }
        } catch (Exception e) {
            log.error("Failed to update datalake upload status for: {}", zipFileName, e);
        }
    }

    /**
     * Updates the backup path for a zip file.
     *
     * @param zipFileName the name of the zip file
     * @param backupPath the backup folder path
     */
    @Transactional
    public void updateBackupPath(String zipFileName, String backupPath) {
        try {
            FileTransferZipTrackingEntity entity = zipTrackingRepository.findByFinalZipName(zipFileName);
            if (entity != null) {
                entity.setBACKUP_PATH(backupPath);
                zipTrackingRepository.save(entity);
                log.info("Updated backup path for: {} to {}", zipFileName, backupPath);
            }
        } catch (Exception e) {
            log.error("Failed to update backup path for: {}", zipFileName, e);
        }
    }

    /**
     * Retrieves all tracking records for a specific data source.
     *
     * @param dataSource the data source name
     * @return list of tracking entities
     */
    public List<FileTransferZipTrackingEntity> getTrackingByDataSource(String dataSource) {
        return zipTrackingRepository.findByDataSource(dataSource);
    }

    /**
     * Retrieves all tracking records for a specific data source and environment.
     *
     * @param dataSource the data source name
     * @param environment the environment name
     * @return list of tracking entities
     */
    public List<FileTransferZipTrackingEntity> getTrackingByDataSourceAndEnvironment(String dataSource, String environment) {
        return zipTrackingRepository.findByDataSourceAndEnvironment(dataSource, environment);
    }

    /**
     * Retrieves tracking records created after a specific timestamp.
     *
     * @param timestamp the timestamp to filter by
     * @return list of tracking entities
     */
    public List<FileTransferZipTrackingEntity> getTrackingAfterTimestamp(LocalDateTime timestamp) {
        return zipTrackingRepository.findByCreatedTimestampAfter(timestamp);
    }

    /**
     * Creates a new ZipTrackingDTO builder for collecting tracking data.
     *
     * @param zipFileName the name of the final zip file
     * @param dataSource the data source name
     * @param environment the environment name
     * @return a new builder instance
     */
    public ZipTrackingDTOBuilder createTrackingBuilder(String zipFileName, String dataSource, String environment) {
        return new ZipTrackingDTOBuilder(zipFileName, dataSource, environment);
    }

    /**
     * Builder class for constructing ZipTrackingDTO objects during file processing.
     */
    public static class ZipTrackingDTOBuilder {
        private final String finalZipName;
        private final String dataSource;
        private final String environment;
        private final Set<String> sourceFolderPaths;
        private final List<FileInfoDTO> filesInfo;
        private Long zipSizeBytes;
        private String backupPath;
        private Boolean uploadedToDatalake;

        public ZipTrackingDTOBuilder(String finalZipName, String dataSource, String environment) {
            this.finalZipName = finalZipName;
            this.dataSource = dataSource;
            this.environment = environment;
            this.sourceFolderPaths = new HashSet<>();
            this.filesInfo = new ArrayList<>();
            this.uploadedToDatalake = false;
        }

        /**
         * Adds a source folder path to the tracking data.
         */
        public ZipTrackingDTOBuilder addSourceFolderPath(String folderPath) {
            this.sourceFolderPaths.add(folderPath);
            return this;
        }

        /**
         * Adds a file that came directly from a folder (not extracted from an archive).
         */
        public ZipTrackingDTOBuilder addDirectFile(String fileName, Long fileSize, String originalFolderPath) {
            FileInfoDTO fileInfo = FileInfoDTO.builder()
                    .fileName(fileName)
                    .source("direct")
                    .fileSizeBytes(fileSize)
                    .originalFolderPath(originalFolderPath)
                    .build();
            this.filesInfo.add(fileInfo);
            return this;
        }

        /**
         * Adds a file that was extracted from a compressed archive.
         */
        public ZipTrackingDTOBuilder addExtractedFile(String fileName, String originalZipName, Long fileSize, String originalFolderPath) {
            FileInfoDTO fileInfo = FileInfoDTO.builder()
                    .fileName(fileName)
                    .source("extracted")
                    .originalZip(originalZipName)
                    .fileSizeBytes(fileSize)
                    .originalFolderPath(originalFolderPath)
                    .build();
            this.filesInfo.add(fileInfo);
            return this;
        }

        /**
         * Adds a FileInfoDTO directly.
         */
        public ZipTrackingDTOBuilder addFileInfo(FileInfoDTO fileInfo) {
            this.filesInfo.add(fileInfo);
            return this;
        }

        /**
         * Sets the final zip file size.
         */
        public ZipTrackingDTOBuilder setZipSize(Long sizeBytes) {
            this.zipSizeBytes = sizeBytes;
            return this;
        }

        /**
         * Sets the backup path where the zip was moved.
         */
        public ZipTrackingDTOBuilder setBackupPath(String backupPath) {
            this.backupPath = backupPath;
            return this;
        }

        /**
         * Sets the datalake upload status.
         */
        public ZipTrackingDTOBuilder setUploadedToDatalake(Boolean uploaded) {
            this.uploadedToDatalake = uploaded;
            return this;
        }

        /**
         * Builds the final ZipTrackingDTO.
         */
        public ZipTrackingDTO build() {
            return ZipTrackingDTO.builder()
                    .finalZipName(finalZipName)
                    .dataSource(dataSource)
                    .environment(environment)
                    .sourceFolderPaths(sourceFolderPaths)
                    .filesInfo(filesInfo)
                    .zipSizeBytes(zipSizeBytes)
                    .totalFilesCount(filesInfo.size())
                    .createdTimestamp(LocalDateTime.now())
                    .backupPath(backupPath)
                    .uploadedToDatalake(uploadedToDatalake)
                    .build();
        }
    }
}
