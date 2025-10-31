package org.ft.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Data Transfer Object for tracking a final zip file.
 * Contains all necessary information to trace back files to their original sources.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZipTrackingDTO {

    @JsonProperty("final_zip_name")
    private String finalZipName;

    @JsonProperty("data_source")
    private String dataSource;

    @JsonProperty("environment")
    private String environment;

    @JsonProperty("source_folder_paths")
    private Set<String> sourceFolderPaths;

    @JsonProperty("files_info")
    private List<FileInfoDTO> filesInfo;

    @JsonProperty("zip_size_bytes")
    private Long zipSizeBytes;

    @JsonProperty("total_files_count")
    private Integer totalFilesCount;

    @JsonProperty("created_timestamp")
    private LocalDateTime createdTimestamp;

    @JsonProperty("backup_path")
    private String backupPath;

    @JsonProperty("original_backup_path")
    private String originalBackupPath;

    @JsonProperty("uploaded_to_datalake")
    private Boolean uploadedToDatalake;
}
