package org.ft.util;

import lombok.Data;
import org.ft.dto.FileInfoDTO;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context for tracking file lineage during processing.
 * Maintains relationships between files and their original sources throughout
 * the move and extraction process.
 */
@Data
public class FileTrackingContext {

    // Map: temp file name -> FileInfoDTO (tracks all files in temp folder)
    private final Map<String, FileInfoDTO> fileMetadata;

    // Map: temp file name -> original folder path
    private final Map<String, String> fileToFolderPath;

    // Map: extracted file name -> source zip name
    private final Map<String, String> extractedFileToZip;

    // Set of all original folder paths being processed
    private final Set<String> sourceFolderPaths;

    // Current environment being processed
    private final String environment;

    public FileTrackingContext(String environment) {
        this.environment = environment;
        this.fileMetadata = new ConcurrentHashMap<>();
        this.fileToFolderPath = new ConcurrentHashMap<>();
        this.extractedFileToZip = new ConcurrentHashMap<>();
        this.sourceFolderPaths = new HashSet<>();
    }

    /**
     * Records that a file was moved directly from a folder to temp.
     *
     * @param fileName the file name
     * @param originalFolderPath the original folder path
     * @param fileSize the file size in bytes
     */
    public void trackDirectFile(String fileName, String originalFolderPath, Long fileSize) {
        sourceFolderPaths.add(originalFolderPath);
        fileToFolderPath.put(fileName, originalFolderPath);

        FileInfoDTO fileInfo = FileInfoDTO.builder()
                .fileName(fileName)
                .source("direct")
                .fileSizeBytes(fileSize)
                .originalFolderPath(originalFolderPath)
                .build();

        fileMetadata.put(fileName, fileInfo);
    }

    /**
     * Records that a file was extracted from a compressed archive.
     *
     * @param extractedFileName the extracted file name
     * @param sourceZipName the original zip file name
     * @param fileSize the file size in bytes
     */
    public void trackExtractedFile(String extractedFileName, String sourceZipName, Long fileSize) {
        // Get the original folder path from the source zip
        String originalFolderPath = fileToFolderPath.get(sourceZipName);

        extractedFileToZip.put(extractedFileName, sourceZipName);

        FileInfoDTO fileInfo = FileInfoDTO.builder()
                .fileName(extractedFileName)
                .source("extracted")
                .originalZip(sourceZipName)
                .fileSizeBytes(fileSize)
                .originalFolderPath(originalFolderPath)
                .build();

        fileMetadata.put(extractedFileName, fileInfo);

        // Also track that this file belongs to the same folder as the zip
        if (originalFolderPath != null) {
            fileToFolderPath.put(extractedFileName, originalFolderPath);
        }
    }

    /**
     * Gets the FileInfoDTO for a specific file.
     *
     * @param fileName the file name
     * @return the file info, or null if not tracked
     */
    public FileInfoDTO getFileInfo(String fileName) {
        return fileMetadata.get(fileName);
    }

    /**
     * Gets all FileInfoDTO objects for files in the given list.
     *
     * @param fileNames list of file names
     * @return list of FileInfoDTO objects
     */
    public List<FileInfoDTO> getFileInfoList(List<String> fileNames) {
        List<FileInfoDTO> result = new ArrayList<>();
        for (String fileName : fileNames) {
            FileInfoDTO info = fileMetadata.get(fileName);
            if (info != null) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * Gets all source folder paths that contributed files to this context.
     *
     * @return set of folder paths
     */
    public Set<String> getAllSourceFolderPaths() {
        return new HashSet<>(sourceFolderPaths);
    }

    /**
     * Removes tracking for a file (e.g., when it's moved to error folder).
     *
     * @param fileName the file name to remove
     */
    public void removeFile(String fileName) {
        fileMetadata.remove(fileName);
        fileToFolderPath.remove(fileName);
        extractedFileToZip.remove(fileName);
    }

    /**
     * Gets the count of tracked files.
     *
     * @return number of files being tracked
     */
    public int getTrackedFileCount() {
        return fileMetadata.size();
    }
}
