package org.ft.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity for tracking final zip files and their source information.
 * Each record represents one final zip file with complete traceability
 * of all files it contains and their origins.
 */
@Entity
@Getter
@Setter
@Table(name = "file_transfer_zip_tracking")
public class FileTransferZipTrackingEntity {

    @JsonProperty("ID")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ID;

    @JsonProperty("FINAL_ZIP_NAME")
    @Column(name = "FINAL_ZIP_NAME", nullable = false, length = 500)
    private String FINAL_ZIP_NAME;

    @JsonProperty("DATA_SOURCE")
    @Column(name = "DATA_SOURCE", nullable = false, length = 100)
    private String DATA_SOURCE;

    @JsonProperty("ENVIRONMENT")
    @Column(name = "ENVIRONMENT", nullable = false, length = 50)
    private String ENVIRONMENT;

    @JsonProperty("SOURCE_FOLDER_PATHS")
    @Column(name = "SOURCE_FOLDER_PATHS", columnDefinition = "JSON")
    private String SOURCE_FOLDER_PATHS;

    @JsonProperty("FILES_INFO")
    @Column(name = "FILES_INFO", columnDefinition = "JSON")
    private String FILES_INFO;

    @JsonProperty("ZIP_SIZE_BYTES")
    @Column(name = "ZIP_SIZE_BYTES")
    private Long ZIP_SIZE_BYTES;

    @JsonProperty("TOTAL_FILES_COUNT")
    @Column(name = "TOTAL_FILES_COUNT")
    private Integer TOTAL_FILES_COUNT;

    @JsonProperty("CREATED_TIMESTAMP")
    @Column(name = "CREATED_TIMESTAMP", nullable = false)
    private LocalDateTime CREATED_TIMESTAMP;

    @JsonProperty("BACKUP_PATH")
    @Column(name = "BACKUP_PATH", length = 1000)
    private String BACKUP_PATH;

    @JsonProperty("ORIGINAL_BACKUP_PATH")
    @Column(name = "ORIGINAL_BACKUP_PATH", length = 1000)
    private String ORIGINAL_BACKUP_PATH;

    @JsonProperty("UPLOADED_TO_DATALAKE")
    @Column(name = "UPLOADED_TO_DATALAKE")
    private Boolean UPLOADED_TO_DATALAKE;

    @JsonProperty("CREATION_DATE")
    @Column(name = "CREATION_DATE", insertable = false, updatable = false)
    private String CREATION_DATE;
}
