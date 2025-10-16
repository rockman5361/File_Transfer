package org.ft.scheduler;

import com.google.gson.Gson;
import lombok.extern.log4j.Log4j2;
import org.ft.entity.FileTransferFolderPathEntity;
import org.ft.entity.FileTransferDataSourceEntity;
import org.ft.repository.CustomMapper;
import org.ft.services.FileProcessorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@Log4j2
public class Scheduler {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(50);
        return taskScheduler;
    }

    @Autowired
    private FileProcessorService fileProcessorService;

    @Autowired
    private CustomMapper mapper;


    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
    private static boolean isRunning = false;

    @Scheduled(cron = "0 */1 * * * *")
    public void runTask() throws Exception {
        if (isRunning) {
            return; // Prevent concurrent execution
        }

        log.info("Scheduler started at: {}", dateTimeFormatter.format(java.time.LocalDateTime.now()));

        isRunning = true;

        List<FileTransferDataSourceEntity> fileTransferDataSources = mapper.getFileTransferDataSources();

        log.debug("File transfer data sources: {}", new Gson().toJson(fileTransferDataSources));

        CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                fileTransferDataSources.stream()
                        .map(entity -> CompletableFuture.runAsync(() -> {
                            try {
                                List<FileTransferFolderPathEntity> folderPaths = mapper.getFileTransferFolderPathByDataSourceId(entity.getID());

                                log.debug("Folder paths for data source {}: {}", entity.getID(), new Gson().toJson(folderPaths));

                                // Process each data source concurrently
                                fileProcessorService.processFileTransferDataSource(entity, folderPaths);
                            } catch (Exception e) {
                                log.error("Error processing data source {}: {}", entity.getDATA_SOURCE(), e.getMessage(), e);
                            }
                        }))
                        .toArray(CompletableFuture[]::new)
        );

        allTasks.thenRun(() -> {
            isRunning = false;
        });

        log.info("Scheduler ended at: {}", dateTimeFormatter.format(java.time.LocalDateTime.now()));
    }

    @Scheduled(cron = "0 0 0 * * *") // Every day at midnight
    public void deleteFiles() {
        log.info("Scheduler for deleting old files started at: {}", dateTimeFormatter.format(java.time.LocalDateTime.now()));
        try {
            List<FileTransferDataSourceEntity> list = mapper.getFileTransferDataSources();
            list.forEach(entity -> {
                fileProcessorService.deleteOldBackupFiles(entity);
                fileProcessorService.deleteOldLogFiles(entity);
            });
        } catch (Exception e) {
            log.error("Error during deletion of old files", e);
        }
        log.info("Scheduler for deleting old files ended at: {}", dateTimeFormatter.format(java.time.LocalDateTime.now()));
    }
}
