-- Migration script to update file_transfer_error_log table structure
-- This script removes FILE_LIST column and adds FOLDER_PATH and ORIGINAL_ARCHIVE_FILE_NAME columns

USE file_transfer_db;

-- Add new columns
ALTER TABLE file_transfer_error_log
ADD COLUMN FOLDER_PATH TEXT NULL AFTER FILE_NAME,
ADD COLUMN ORIGINAL_ARCHIVE_FILE_NAME VARCHAR(500) NULL AFTER FOLDER_PATH;

-- Drop old column
ALTER TABLE file_transfer_error_log
DROP COLUMN FILE_LIST;

-- Verify the changes
DESCRIBE file_transfer_error_log;
