package org.ft.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@Table(name = "file_transfer_data_source")
public class FileTransferDataSourceEntity {

    @JsonProperty("ID")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Use IDENTITY when the database generates the ID
    private Integer ID;

    @JsonProperty("DATA_SOURCE")
    private String DATA_SOURCE;

    @JsonProperty("ACTIVE")
    private Integer ACTIVE;

    @JsonProperty("CREATION_DATE")
    @Column(name = "CREATION_DATE", insertable = false, updatable = false)
    private String CREATION_DATE;
}
