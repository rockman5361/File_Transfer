package org.ft.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.ft.entity.FileTransferErrorLogEntity;
import org.ft.enums.ErrorType;
import org.ft.repository.FileTransferErrorLogRepository;
import org.ft.repository.CustomMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Collectors;

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
     * Inserts or updates an error log entry.
     *
     * @param dataSource the data source name
     * @param environment the environment name
     * @param fileName the file name that caused the error
     * @param errorMessage the error type
     * @param fileList the list of affected files
     */
    public void insertOrUpdateErrorLog(String dataSource, String environment, String fileName, ErrorType errorMessage, List<String> fileList) {
        log.debug("Processing error log - Data Source: {}, Environment: {}, File Name: {}, Error: {}, File List: {}",
            dataSource, environment, fileName, errorMessage.name(), fileList);

        FileTransferErrorLogEntity entity = getErrorLog(fileName, dataSource, environment, errorMessage.name());
        Gson gson = new Gson();

        if (entity == null) {
            log.info("Creating new error log entry for file: {} in data source: {}", fileName, dataSource);
            entity = new FileTransferErrorLogEntity();
            entity.setID(generateErrorLogId());
            entity.setDATA_SOURCE(dataSource);
            entity.setENVIRONMENT(environment);
            entity.setFILE_NAME(fileName);
            entity.setERROR_MESSAGE(errorMessage.name());
            entity.setFILE_LIST(gson.toJson(fileList));
        } else {
            log.info("Updating existing error log entry for file: {} in data source: {}", fileName, dataSource);
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> existingFileList = gson.fromJson(entity.getFILE_LIST(), listType);
            existingFileList.addAll(fileList);
            entity.setFILE_LIST(gson.toJson(existingFileList.stream().distinct().collect(Collectors.toList())));
        }

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
