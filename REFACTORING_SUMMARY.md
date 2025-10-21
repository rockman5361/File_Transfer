# File Transfer Error Logging Refactoring Summary

## Overview
This document summarizes the refactoring of the error logging system for the File Transfer application. The changes implement a new error logging strategy where each file error gets its own database row, with enhanced tracking of folder paths and archive file information.

---

## Database Schema Changes

### 1. `file_transfer_error_log` Table Structure

**Removed Columns:**
- `FILE_LIST` (TEXT) - Previously stored JSON array of all affected files

**Added Columns:**
- `FOLDER_PATH` (TEXT) - Stores the original folder path where the file came from (e.g., "D:\Backend\TEST")
- `ORIGINAL_ARCHIVE_FILE_NAME` (VARCHAR 500) - Stores the archive file name if the file was extracted from a zip (first-level only, nested archives are ignored)

**Final Schema:**
```sql
CREATE TABLE file_transfer_error_log (
    ID VARCHAR(255) PRIMARY KEY,
    ENVIRONMENT VARCHAR(255) NULL,
    DATA_SOURCE VARCHAR(255) NULL,
    ERROR_MESSAGE TEXT NULL,
    FILE_NAME VARCHAR(255) NULL,
    FOLDER_PATH TEXT NULL,
    ORIGINAL_ARCHIVE_FILE_NAME VARCHAR(500) NULL,
    CREATION_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    SOLVED TINYINT(1) DEFAULT 0,
    SOLVED_DATE VARCHAR(255) NULL,
    INDEX idx_environment (ENVIRONMENT),
    INDEX idx_data_source (DATA_SOURCE),
    INDEX idx_solved (SOLVED)
);
```

**Migration Script:**
- Location: `src/main/resources/db/migration/alter_error_log_table.sql`
- Run this script to update existing database tables

---

## Code Changes

### 1. Entity Updates

**File:** `src/main/java/org/ft/entity/FileTransferErrorLogEntity.java`

Removed field:
```java
private String FILE_LIST;
```

Added fields:
```java
private String FOLDER_PATH;
private String ORIGINAL_ARCHIVE_FILE_NAME;
```

### 2. Service Layer Refactoring

**File:** `src/main/java/org/ft/services/FileTransferErrorLogService.java`

**Old Method (Removed):**
```java
public void insertOrUpdateErrorLog(String dataSource, String environment,
    String fileName, ErrorType errorMessage, List<String> fileList)
```

**New Method:**
```java
public void insertErrorLog(String dataSource, String environment,
    String fileName, ErrorType errorMessage,
    String folderPath, String originalArchiveFileName)
```

**Key Changes:**
- No more merging/updating of existing error logs
- Each error creates a new row in the database
- Simplified logic - always creates new entries
- Removed Gson dependency for file list serialization

### 3. FileTrackingContext Enhancements

**File:** `src/main/java/org/ft/util/FileTrackingContext.java`

Added helper methods:
```java
public String getFolderPath(String fileName)
public String getOriginalArchiveName(String fileName)
```

These methods retrieve:
- The original folder path for any tracked file
- The first-level archive name (null for direct files or nested archives)

### 4. FileProcessorService Updates

**File:** `src/main/java/org/ft/services/FileProcessorService.java`

All error logging calls updated to use the new `insertErrorLog()` method with folder path and archive information.

**Changes by Error Type:**

#### DUPLICATE_FILE Errors
- **Location 1:** `fileExists()` method (line ~498-523)
  - Creates separate error log rows for each duplicate file
  - Tracks both original and renamed files individually

- **Location 2:** `writeToFile()` method (line ~604-616)
  - Logs duplicate files found during extraction
  - Includes archive information

- **Location 3:** `extract7z()` method (line ~646-658)
  - Logs duplicate files from 7z archives
  - Includes archive information

- **Location 4:** `getUniqueFileName()` method (line ~679-695)
  - Logs duplicate files during unique naming
  - Single error log per file

#### WRONG_FILE_TYPE Errors
- **Location:** `extractCompressedFiles()` method (line ~420-436)
  - Creates individual error log for each non-XML file
  - Includes folder path and archive info if extracted

#### EXTRACTION_ERROR Errors
- **Location 1:** Directory extraction (line ~389-408)
  - Logs archive files that fail to extract
  - Includes folder path and parent archive info if nested

- **Location 2:** File extraction (line ~411-433)
  - Logs compressed files that fail to extract
  - Includes folder path and parent archive info if nested

---

## Error Logging Logic

### 1. DUPLICATE_FILE
**When:** XML files have the same name (e.g., document.xml, document(1).xml, document(2).xml)

**Behavior:**
- Create **separate rows** for each duplicate file
- Each row has its own `FILE_NAME` (e.g., "document(1).xml")
- `FOLDER_PATH`: Original source folder
- `ORIGINAL_ARCHIVE_FILE_NAME`:
  - If extracted from archive → archive name (e.g., "batch_20241021.zip")
  - If direct file → null

**Example:**
```
Row 1:
  FILE_NAME: document.xml
  FOLDER_PATH: D:\Backend\TEST
  ORIGINAL_ARCHIVE_FILE_NAME: null

Row 2:
  FILE_NAME: document(1).xml
  FOLDER_PATH: D:\Backend\TEST
  ORIGINAL_ARCHIVE_FILE_NAME: null
```

### 2. WRONG_FILE_TYPE
**When:** Files are not in XML format

**Behavior:**
- Create **separate rows** for each wrong-format file
- If multiple files with similar names (e.g., document.txt, document(1).txt)
- Each gets its own row with unique `FILE_NAME`

**Example:**
```
Row 1:
  FILE_NAME: document.txt
  FOLDER_PATH: D:\Backend\TEST
  ORIGINAL_ARCHIVE_FILE_NAME: batch_20241021.zip

Row 2:
  FILE_NAME: report.pdf
  FOLDER_PATH: D:\Backend\TEST
  ORIGINAL_ARCHIVE_FILE_NAME: batch_20241021.zip
```

### 3. EXTRACTION_ERROR
**When:** Archive file cannot be extracted

**Behavior:**
- Log the archive file name that failed
- `FILE_NAME`: The corrupted/failed archive name
- `ORIGINAL_ARCHIVE_FILE_NAME`: null (the file itself is the archive)

**Example:**
```
FILE_NAME: corrupted_archive.zip
FOLDER_PATH: D:\Backend\TEST
ORIGINAL_ARCHIVE_FILE_NAME: null
```

---

## ORIGINAL_ARCHIVE_FILE_NAME Rules

### Population Rules:
1. **Only populate** when the file came from a zip/archive
2. **Store only first-level archive name** (e.g., "archive.zip")
3. **Ignore nested archives** - if there's a zip inside a zip, only track the outermost zip
4. **Leave empty/null** for files that were not extracted from archives

### Examples:

**Scenario 1:** Direct file from folder
```
FILE_NAME: document.xml
FOLDER_PATH: D:\Backend\TEST
ORIGINAL_ARCHIVE_FILE_NAME: null
```

**Scenario 2:** File extracted from archive
```
FILE_NAME: document.xml
FOLDER_PATH: D:\Backend\TEST
ORIGINAL_ARCHIVE_FILE_NAME: batch_20241021.zip
```

**Scenario 3:** File from nested archive (outer.zip contains inner.zip contains file.xml)
```
FILE_NAME: file.xml
FOLDER_PATH: D:\Backend\TEST
ORIGINAL_ARCHIVE_FILE_NAME: inner.zip  (only immediate parent archive)
```

---

## Files_Info Column in file_transfer_zip_tracking

The `FILES_INFO` column in the `file_transfer_zip_tracking` table already tracks first-level archive information correctly. No changes needed.

**Behavior:**
- Only tracks immediate archive information
- Does not include nested archives (archives within archives)
- Only stores the immediate archive file name in the FileInfoDTO

---

## Testing Scenarios

### Scenario 1: Duplicate XML from Folder
**Setup:** Two files named "document.xml" in the same folder

**Expected Result:**
```sql
-- Row 1
ENVIRONMENT: stage
DATA_SOURCE: E142_TEST
ERROR_MESSAGE: DUPLICATE_FILE
FILE_NAME: document.xml
FOLDER_PATH: D:\Backend\TEST
ORIGINAL_ARCHIVE_FILE_NAME: null

-- Row 2
ENVIRONMENT: stage
DATA_SOURCE: E142_TEST
ERROR_MESSAGE: DUPLICATE_FILE
FILE_NAME: document(1).xml
FOLDER_PATH: D:\Backend\TEST
ORIGINAL_ARCHIVE_FILE_NAME: null
```

### Scenario 2: Wrong File Type from Archive
**Setup:** "document.txt" extracted from "batch_20241021.zip"

**Expected Result:**
```sql
ENVIRONMENT: stage
DATA_SOURCE: E142_TEST
ERROR_MESSAGE: WRONG_FILE_TYPE
FILE_NAME: document.txt
FOLDER_PATH: D:\Backend\TEST
ORIGINAL_ARCHIVE_FILE_NAME: batch_20241021.zip
```

### Scenario 3: Extraction Error
**Setup:** "corrupted_archive.zip" fails to extract

**Expected Result:**
```sql
ENVIRONMENT: stage
DATA_SOURCE: E142_TEST
ERROR_MESSAGE: EXTRACTION_ERROR
FILE_NAME: corrupted_archive.zip
FOLDER_PATH: D:\Backend\TEST
ORIGINAL_ARCHIVE_FILE_NAME: null
```

### Scenario 4: Multiple Wrong File Types from Same Archive
**Setup:** "doc1.txt" and "doc2.pdf" both from "batch.zip"

**Expected Result:**
```sql
-- Row 1
ENVIRONMENT: stage
DATA_SOURCE: E142_TEST
ERROR_MESSAGE: WRONG_FILE_TYPE
FILE_NAME: doc1.txt
FOLDER_PATH: D:\Backend\TEST
ORIGINAL_ARCHIVE_FILE_NAME: batch.zip

-- Row 2
ENVIRONMENT: stage
DATA_SOURCE: E142_TEST
ERROR_MESSAGE: WRONG_FILE_TYPE
FILE_NAME: doc2.pdf
FOLDER_PATH: D:\Backend\TEST
ORIGINAL_ARCHIVE_FILE_NAME: batch.zip
```

---

## Migration Steps

### 1. Database Migration
```bash
# Run the migration script
mysql -u root -p file_transfer_db < src/main/resources/db/migration/alter_error_log_table.sql
```

### 2. Code Deployment
1. Stop the application
2. Deploy the updated code
3. Start the application

### 3. Verification
1. Check database schema:
   ```sql
   DESCRIBE file_transfer_error_log;
   ```

2. Test error scenarios:
   - Place duplicate XML files in a monitored folder
   - Place non-XML files in a monitored folder
   - Place a corrupted archive in a monitored folder

3. Verify error logs:
   ```sql
   SELECT * FROM file_transfer_error_log
   ORDER BY CREATION_DATE DESC
   LIMIT 10;
   ```

---

## Benefits of New Approach

1. **Better Traceability**
   - Each error has complete context (folder path + archive info)
   - Easy to identify where problematic files originated

2. **Simplified Logic**
   - No more complex merge/update logic
   - Straightforward insert operations

3. **Improved Reporting**
   - Can easily count duplicate files
   - Can track which archives produce errors
   - Can identify problematic source folders

4. **Data Integrity**
   - Each file error is independent
   - No risk of losing information during merges
   - Complete audit trail

5. **Query Flexibility**
   - Can filter by folder path
   - Can group by archive name
   - Can analyze error patterns by source

---

## Notes

- The `getErrorLog()` method in FileTransferErrorLogService is now unused but kept for potential future use
- The CustomMapper query remains unchanged for backward compatibility
- All error logging now happens immediately when errors are detected
- The FileTrackingContext provides complete lineage information throughout the processing pipeline

---

## Future Enhancements

Potential improvements for consideration:
1. Add indexes on FOLDER_PATH and ORIGINAL_ARCHIVE_FILE_NAME for faster queries
2. Add timestamp for when file was first seen in the system
3. Add file size to error log for better analysis
4. Add checksum/hash for duplicate detection beyond filename
5. Add retry mechanism for extraction errors

---

## Contact

For questions or issues related to this refactoring, please contact the development team or create an issue in the project repository.
