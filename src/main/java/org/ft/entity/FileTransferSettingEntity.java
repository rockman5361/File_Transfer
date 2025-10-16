package org.ft.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@Table(name = "file_transfer_setting")
public class FileTransferSettingEntity {

    public enum Type {
        MAX_ZIP_SIZE,
    }

    @JsonProperty("ID")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Use IDENTITY when the database generates the ID
    private Integer ID;

    @JsonProperty("TYPE")
    private String TYPE;

    @JsonProperty("VALUE")
    private String VALUE;

    @JsonProperty("CREATION_DATE")
    @Column(name = "CREATION_DATE", insertable = false, updatable = false)
    private String CREATION_DATE;
}
