-- Create table for tracking final zip files and their source information
CREATE TABLE IF NOT EXISTS file_transfer_zip_tracking (
    ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    FINAL_ZIP_NAME VARCHAR(500) NOT NULL,
    DATA_SOURCE VARCHAR(100) NOT NULL,
    ENVIRONMENT VARCHAR(50) NOT NULL,
    SOURCE_FOLDER_PATHS JSON COMMENT 'Array of original folder paths that contributed files to this zip',
    FILES_INFO JSON COMMENT 'Detailed information about each file in the zip including source and origin',
    ZIP_SIZE_BYTES BIGINT COMMENT 'Size of the final zip file in bytes',
    TOTAL_FILES_COUNT INT COMMENT 'Total number of files in the zip',
    CREATED_TIMESTAMP DATETIME NOT NULL COMMENT 'When the zip file was created',
    BACKUP_PATH VARCHAR(1000) COMMENT 'Path where the zip file was backed up',
    UPLOADED_TO_DATALAKE BOOLEAN DEFAULT FALSE COMMENT 'Whether the zip was uploaded to datalake',
    CREATION_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_data_source (DATA_SOURCE),
    INDEX idx_environment (ENVIRONMENT),
    INDEX idx_created_timestamp (CREATED_TIMESTAMP),
    INDEX idx_final_zip_name (FINAL_ZIP_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Tracks final zip files with complete traceability to original folder paths and extracted archives';

-- Example JSON structure for SOURCE_FOLDER_PATHS:
-- [
--   "/path/to/folder1",
--   "/path/to/folder2"
-- ]

-- Example JSON structure for FILES_INFO:
-- [
--   {
--     "file_name": "document.pdf",
--     "source": "direct",
--     "file_size_bytes": 1024,
--     "original_folder_path": "/path/to/folder1"
--   },
--   {
--     "file_name": "image.jpg",
--     "source": "extracted",
--     "original_zip": "archive.zip",
--     "file_size_bytes": 2048,
--     "original_folder_path": "/path/to/folder2"
--   }
-- ]
