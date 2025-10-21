package org.ft.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

@Entity
@Getter
@Setter
@Table(name = "file_transfer_error_log")
public class FileTransferErrorLogEntity {

    @JsonProperty("ID")
    @Id
    private String ID;

    @JsonProperty("ENVIRONMENT")
    private String ENVIRONMENT;

    @JsonProperty("DATA_SOURCE")
    private String DATA_SOURCE;

    @JsonProperty("ERROR_MESSAGE")
    private String ERROR_MESSAGE;

    @JsonProperty("FILE_NAME")
    private String FILE_NAME;

    @JsonProperty("FOLDER_PATH")
    private String FOLDER_PATH;

    @JsonProperty("ORIGINAL_ARCHIVE_FILE_NAME")
    private String ORIGINAL_ARCHIVE_FILE_NAME;

    @JsonProperty("CREATION_DATE")
    @Column(name = "CREATION_DATE", insertable = false, updatable = false)
    private String CREATION_DATE;

    @JsonProperty("SOLVED")
    @Column(name = "SOLVED", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean SOLVED = false;

    @JsonProperty("SOLVED_DATE")
    private String SOLVED_DATE;
}
