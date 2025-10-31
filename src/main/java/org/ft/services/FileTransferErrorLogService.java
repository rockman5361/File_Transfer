package org.ft.services;

import lombok.extern.slf4j.Slf4j;
import org.ft.entity.FileTransferErrorLogEntity;
import org.ft.enums.ErrorType;
import org.ft.repository.FileTransferErrorLogRepository;
import org.ft.repository.CustomMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for managing file transfer error logs
 */
@Service
@Slf4j
public class FileTransferErrorLogService {

    @Autowired
    private CustomMapper customMapper;

    @Autowired
    private FileTransferErrorLogRepository fileTransferErrorLogRepository;

    /**
     * Inserts a single error log entry for a file.
     * Creates a new row for each file error (no more updating/merging).
     *
     * @param dataSource the data source name
     * @param environment the environment name
     * @param fileName the file name that caused the error
     * @param errorMessage the error type
     * @param folderPath the folder path where the file came from
     * @param originalArchiveFileName the archive file name if extracted (first-level only)
     * @param originalBackupPath the path to the original backup folder
     */
    public void insertErrorLog(String dataSource, String environment, String fileName, ErrorType errorMessage,
                               String folderPath, String originalArchiveFileName, String originalBackupPath) {
        log.debug("Creating error log - Data Source: {}, Environment: {}, File Name: {}, Error: {}, Folder Path: {}, Archive: {}, Original Backup: {}",
            dataSource, environment, fileName, errorMessage.name(), folderPath, originalArchiveFileName, originalBackupPath);

        log.info("Creating new error log entry for file: {} in data source: {}", fileName, dataSource);
        FileTransferErrorLogEntity entity = new FileTransferErrorLogEntity();
        entity.setID(generateErrorLogId());
        entity.setDATA_SOURCE(dataSource);
        entity.setENVIRONMENT(environment);
        entity.setFILE_NAME(fileName);
        entity.setERROR_MESSAGE(errorMessage.name());
        entity.setFOLDER_PATH(folderPath);
        entity.setORIGINAL_BACKUP_PATH(originalBackupPath);
        entity.setORIGINAL_ARCHIVE_FILE_NAME(originalArchiveFileName);

        fileTransferErrorLogRepository.saveAndFlush(entity);
    }

    public FileTransferErrorLogEntity getErrorLog(String fileName, String dataSource, String environment, String errorMessage) {
        return customMapper.getFileTransferErrorLog(fileName, dataSource, environment, errorMessage);
    }

    public String generateErrorLogId() {
        String id = "FTEL" + System.currentTimeMillis();
        while (customMapper.isExistFileTransferErrorLogId(id)) {
            id = "FTEL" + System.currentTimeMillis();
        }
        return id;
    }
}
