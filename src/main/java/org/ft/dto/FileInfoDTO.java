package org.ft.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for individual file information within a zip.
 * Tracks whether a file came directly from a folder or was extracted from an archive.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfoDTO {

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("source")
    private String source; // "direct" or "extracted"

    @JsonProperty("original_zip")
    private String originalZip; // Only present if source is "extracted"

    @JsonProperty("file_size_bytes")
    private Long fileSizeBytes;

    @JsonProperty("original_folder_path")
    private String originalFolderPath; // The source folder path this file came from
}
