package org.ft.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@Table(name = "file_transfer_folder_path")
public class FileTransferFolderPathEntity {

    @JsonProperty("ID")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Use IDENTITY when the database generates the ID
    private Integer ID;

    @JsonProperty("DATA_SOURCE_ID")
    private Integer DATA_SOURCE_ID;

    @JsonProperty("ENVIRONMENT")
    private String ENVIRONMENT;

    @JsonProperty("FOLDER_PATH")
    private String FOLDER_PATH;

    @JsonProperty("SITE")
    private String SITE;

    @JsonProperty("ACTIVE")
    private Integer ACTIVE;

    @JsonProperty("CREATION_DATE")
    @Column(name = "CREATION_DATE", insertable = false, updatable = false)
    private String CREATION_DATE;
}
