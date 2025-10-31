package org.ft.util;

import lombok.Getter;
import org.ft.dto.FileInfoDTO;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context for tracking file lineage during processing.
 * Maintains relationships between files and their original sources throughout
 * the move and extraction process.
 */
public class FileTrackingContext {

    // Map: temp file name -> FileInfoDTO (tracks all files in temp folder)
    private final Map<String, FileInfoDTO> fileMetadata;

    // Map: temp file name -> original folder path
    private final Map<String, String> fileToFolderPath;

    // Map: extracted file name -> source zip name (immediate parent)
    private final Map<String, String> extractedFileToZip;

    // Map: file name -> first-level archive name (root archive only)
    private final Map<String, String> fileToFirstLevelArchive;

    // Set of all original folder paths being processed
    private final Set<String> sourceFolderPaths;

    // Current environment being processed
    @Getter
    private final String environment;

    // Path to original backup folder (before processing)
    @Getter
    private String originalBackupPath;

    public FileTrackingContext(String environment) {
        this.environment = environment;
        this.fileMetadata = new ConcurrentHashMap<>();
        this.fileToFolderPath = new ConcurrentHashMap<>();
        this.extractedFileToZip = new ConcurrentHashMap<>();
        this.fileToFirstLevelArchive = new ConcurrentHashMap<>();
        this.sourceFolderPaths = new HashSet<>();
    }

    /**
     * Sets the original backup path for this context.
     *
     * @param originalBackupPath the path to the original backup folder
     */
    public void setOriginalBackupPath(String originalBackupPath) {
        this.originalBackupPath = originalBackupPath;
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
     * @param sourceZipName the immediate parent archive file name
     * @param fileSize the file size in bytes
     */
    public void trackExtractedFile(String extractedFileName, String sourceZipName, Long fileSize) {
        // Get the original folder path from the source zip
        String originalFolderPath = fileToFolderPath.get(sourceZipName);

        extractedFileToZip.put(extractedFileName, sourceZipName);

        // Determine the first-level archive (root archive)
        // If sourceZipName has a first-level archive, use that (nested case)
        // Otherwise, sourceZipName itself is the first-level archive
        String firstLevelArchive = fileToFirstLevelArchive.get(sourceZipName);
        if (firstLevelArchive == null) {
            // sourceZipName is the first-level archive (came directly from folder)
            firstLevelArchive = sourceZipName;
        }
        // Track the first-level archive for this extracted file
        fileToFirstLevelArchive.put(extractedFileName, firstLevelArchive);

        FileInfoDTO fileInfo = FileInfoDTO.builder()
                .fileName(extractedFileName)
                .source("extracted")
                .originalZip(firstLevelArchive)  // Store first-level archive, not immediate parent
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
        fileToFirstLevelArchive.remove(fileName);
    }

    /**
     * Gets the count of tracked files.
     *
     * @return number of files being tracked
     */
    public int getTrackedFileCount() {
        return fileMetadata.size();
    }

    /**
     * Gets the original folder path for a file.
     *
     * @param fileName the file name
     * @return the folder path, or null if not tracked
     */
    public String getFolderPath(String fileName) {
        return fileToFolderPath.get(fileName);
    }

    /**
     * Gets the original archive file name (first-level only) for an extracted file.
     * Returns null if the file was not extracted from any archive.
     * For nested archives, returns the outermost (first-level) archive.
     *
     * @param fileName the file name
     * @return the first-level archive name, or null if not extracted from archive
     */
    public String getOriginalArchiveName(String fileName) {
        return fileToFirstLevelArchive.get(fileName);
    }
}
