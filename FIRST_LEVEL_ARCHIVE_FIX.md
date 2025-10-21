# First-Level Archive Tracking Fix

## Problem Description

### Scenario
When processing nested archives like:
```
archive.tar (first-level, from folder)
  └── innermost.zip (second-level, nested)
      └── file.xml (final file)
```

### Expected Behavior
- `file.xml` should have `ORIGINAL_ARCHIVE_FILE_NAME = archive.tar` (first-level archive)
- `innermost.zip` should have `ORIGINAL_ARCHIVE_FILE_NAME = archive.tar` (first-level archive)

### Actual Behavior (Before Fix)
- `file.xml` had `ORIGINAL_ARCHIVE_FILE_NAME = innermost.zip` (immediate parent, not first-level)
- `innermost.zip` had `ORIGINAL_ARCHIVE_FILE_NAME = archive.tar` (correct)

### Impact
**Affected Tables:**

1. **file_transfer_error_log:**
   - `FTEL1761016140171` - ORIGINAL_ARCHIVE_FILE_NAME should be `archive.tar` ❌ was `innermost.zip`
   - `FTEL1761016140333` - ORIGINAL_ARCHIVE_FILE_NAME should be `archive.tar` ❌ was `innermost.zip`
   - `FTEL1761016140393` - ORIGINAL_ARCHIVE_FILE_NAME should be `archive.tar` ❌ was `innermost.zip`

2. **file_transfer_zip_tracking:**
   - `FILES_INFO` column → `originalZip` should be `archive.tar` ❌ was `innermost.zip`

---

## Root Cause Analysis

### Original Implementation

**FileTrackingContext.java - trackExtractedFile():**
```java
public void trackExtractedFile(String extractedFileName, String sourceZipName, Long fileSize) {
    extractedFileToZip.put(extractedFileName, sourceZipName);  // ❌ Stores immediate parent

    FileInfoDTO fileInfo = FileInfoDTO.builder()
        .originalZip(sourceZipName)  // ❌ Uses immediate parent, not first-level
        .build();
}
```

### Problem Flow

**Step 1:** `archive.tar` is moved from folder
```
trackDirectFile("archive.tar", "D:\Backend\Backup", 1024)
  → extractedFileToZip: {} (empty, not extracted)
  → fileToFolderPath: {"archive.tar": "D:\Backend\Backup"}
```

**Step 2:** `archive.tar` is extracted → creates `innermost.zip`
```
trackExtractedFile("innermost.zip", "archive.tar", 512)
  → extractedFileToZip: {"innermost.zip": "archive.tar"}  ✅ Correct!
  → FileInfoDTO.originalZip = "archive.tar"  ✅ Correct!
```

**Step 3:** `innermost.zip` is extracted → creates `file.xml`
```
trackExtractedFile("file.xml", "innermost.zip", 256)
  → extractedFileToZip: {"file.xml": "innermost.zip"}  ❌ WRONG! Should be "archive.tar"
  → FileInfoDTO.originalZip = "innermost.zip"  ❌ WRONG! Should be "archive.tar"
```

**Issue:** The method only knows the **immediate parent** (`innermost.zip`), not the **root archive** (`archive.tar`).

---

## Solution Implemented

### New Data Structure

Added a new map to track first-level archives separately:

```java
// Map: file name -> first-level archive name (root archive only)
private final Map<String, String> fileToFirstLevelArchive;
```

### Updated trackExtractedFile() Logic

```java
public void trackExtractedFile(String extractedFileName, String sourceZipName, Long fileSize) {
    // Determine the first-level archive (root archive)
    String firstLevelArchive = fileToFirstLevelArchive.get(sourceZipName);
    if (firstLevelArchive == null) {
        // sourceZipName is the first-level archive (came directly from folder)
        firstLevelArchive = sourceZipName;
    }
    // Track the first-level archive for this extracted file
    fileToFirstLevelArchive.put(extractedFileName, firstLevelArchive);

    FileInfoDTO fileInfo = FileInfoDTO.builder()
        .originalZip(firstLevelArchive)  // ✅ Store first-level archive, not immediate parent
        .build();
}
```

### How It Works

**Step 1:** `archive.tar` is moved from folder
```
trackDirectFile("archive.tar", "D:\Backend\Backup", 1024)
  → fileToFirstLevelArchive: {} (empty, direct files don't have archives)
```

**Step 2:** `archive.tar` is extracted → creates `innermost.zip`
```
trackExtractedFile("innermost.zip", "archive.tar", 512)

  1. Check: fileToFirstLevelArchive.get("archive.tar") → null
  2. Since null, "archive.tar" IS the first-level archive
  3. Store: fileToFirstLevelArchive.put("innermost.zip", "archive.tar")

  Result:
  → fileToFirstLevelArchive: {"innermost.zip": "archive.tar"}  ✅
  → FileInfoDTO.originalZip = "archive.tar"  ✅
```

**Step 3:** `innermost.zip` is extracted → creates `file.xml`
```
trackExtractedFile("file.xml", "innermost.zip", 256)

  1. Check: fileToFirstLevelArchive.get("innermost.zip") → "archive.tar"  ✅
  2. Use the existing first-level: "archive.tar"
  3. Store: fileToFirstLevelArchive.put("file.xml", "archive.tar")

  Result:
  → fileToFirstLevelArchive: {"file.xml": "archive.tar"}  ✅
  → FileInfoDTO.originalZip = "archive.tar"  ✅ CORRECT!
```

---

## Code Changes

### File: FileTrackingContext.java

**1. Added new map:**
```java
// Map: file name -> first-level archive name (root archive only)
private final Map<String, String> fileToFirstLevelArchive;
```

**2. Initialize in constructor:**
```java
public FileTrackingContext(String environment) {
    // ... existing code
    this.fileToFirstLevelArchive = new ConcurrentHashMap<>();
}
```

**3. Updated trackExtractedFile():**
```java
public void trackExtractedFile(String extractedFileName, String sourceZipName, Long fileSize) {
    // Determine the first-level archive (root archive)
    String firstLevelArchive = fileToFirstLevelArchive.get(sourceZipName);
    if (firstLevelArchive == null) {
        firstLevelArchive = sourceZipName;  // sourceZipName is first-level
    }
    fileToFirstLevelArchive.put(extractedFileName, firstLevelArchive);

    FileInfoDTO fileInfo = FileInfoDTO.builder()
        .originalZip(firstLevelArchive)  // Use first-level archive
        .build();
    // ... rest of code
}
```

**4. Updated getOriginalArchiveName():**
```java
public String getOriginalArchiveName(String fileName) {
    return fileToFirstLevelArchive.get(fileName);  // Return first-level archive
}
```

**5. Updated removeFile():**
```java
public void removeFile(String fileName) {
    fileMetadata.remove(fileName);
    fileToFolderPath.remove(fileName);
    extractedFileToZip.remove(fileName);
    fileToFirstLevelArchive.remove(fileName);  // Added
}
```

---

## Testing Scenarios

### Test 1: Single-Level Archive
**Setup:** `archive.zip` → `file.xml`

**Expected:**
```
file.xml:
  ORIGINAL_ARCHIVE_FILE_NAME = archive.zip  ✅

file_transfer_zip_tracking:
  FILES_INFO[0].originalZip = archive.zip  ✅
```

### Test 2: Two-Level Nested Archive
**Setup:** `archive.tar` → `innermost.zip` → `file.xml`

**Expected:**
```
innermost.zip:
  ORIGINAL_ARCHIVE_FILE_NAME = archive.tar  ✅

file.xml:
  ORIGINAL_ARCHIVE_FILE_NAME = archive.tar  ✅ (NOT innermost.zip)

file_transfer_zip_tracking:
  FILES_INFO[0].fileName = innermost.zip
  FILES_INFO[0].originalZip = archive.tar  ✅

  FILES_INFO[1].fileName = file.xml
  FILES_INFO[1].originalZip = archive.tar  ✅
```

### Test 3: Three-Level Nested Archive
**Setup:** `outer.zip` → `middle.tar` → `inner.7z` → `file.xml`

**Expected:**
```
All files should have ORIGINAL_ARCHIVE_FILE_NAME = outer.zip  ✅
- middle.tar → outer.zip
- inner.7z → outer.zip
- file.xml → outer.zip
```

### Test 4: Direct File (No Archive)
**Setup:** `file.xml` moved directly from folder

**Expected:**
```
file.xml:
  ORIGINAL_ARCHIVE_FILE_NAME = null  ✅

file_transfer_zip_tracking:
  FILES_INFO[0].originalZip = null  ✅
```

### Test 5: Multiple Archives in Same Batch
**Setup:**
- `archive1.zip` → `file1.xml`
- `archive2.tar` → `file2.xml`

**Expected:**
```
file1.xml → ORIGINAL_ARCHIVE_FILE_NAME = archive1.zip  ✅
file2.xml → ORIGINAL_ARCHIVE_FILE_NAME = archive2.tar  ✅
```

---

## Verification Queries

### Check Error Logs
```sql
-- View error logs with first-level archive tracking
SELECT
    ID,
    FILE_NAME,
    ERROR_MESSAGE,
    FOLDER_PATH,
    ORIGINAL_ARCHIVE_FILE_NAME,
    CREATION_DATE
FROM file_transfer_error_log
WHERE ORIGINAL_ARCHIVE_FILE_NAME IS NOT NULL
ORDER BY CREATION_DATE DESC
LIMIT 10;

-- Expected: ORIGINAL_ARCHIVE_FILE_NAME should always be the outermost archive
```

### Check Zip Tracking
```sql
-- View zip tracking with file lineage
SELECT
    FINAL_ZIP_NAME,
    DATA_SOURCE,
    FILES_INFO
FROM file_transfer_zip_tracking
WHERE FILES_INFO LIKE '%originalZip%'
ORDER BY CREATED_TIMESTAMP DESC
LIMIT 5;

-- Expected: All originalZip values should be first-level archives only
```

### Find Nested Archive Cases
```sql
-- Files extracted from nested archives
SELECT
    FILE_NAME,
    ORIGINAL_ARCHIVE_FILE_NAME,
    ERROR_MESSAGE
FROM file_transfer_error_log
WHERE FILE_NAME LIKE '%.xml'
AND ORIGINAL_ARCHIVE_FILE_NAME LIKE '%.tar'
OR ORIGINAL_ARCHIVE_FILE_NAME LIKE '%.zip';

-- Expected: ORIGINAL_ARCHIVE_FILE_NAME should be the first archive in the chain
```

---

## Edge Cases Handled

### 1. Circular References (Prevented by max iterations)
If somehow archives reference each other, the first-level tracking still works correctly because it's determined at extraction time.

### 2. Mixed Direct and Extracted Files
```
folder/
  ├── direct.xml (no archive)
  └── archive.zip
      └── extracted.xml (from archive)

Result:
  direct.xml → ORIGINAL_ARCHIVE_FILE_NAME = null  ✅
  extracted.xml → ORIGINAL_ARCHIVE_FILE_NAME = archive.zip  ✅
```

### 3. Same Filename in Different Archives
```
archive1.zip → file.xml
archive2.zip → file.xml (renamed to file(1).xml)

Result:
  file.xml → ORIGINAL_ARCHIVE_FILE_NAME = archive1.zip  ✅
  file(1).xml → ORIGINAL_ARCHIVE_FILE_NAME = archive2.zip  ✅
```

### 4. Archive Deleted Before Completion
If archive is deleted mid-processing, first-level tracking still works for already-extracted files.

---

## Data Migration for Existing Records

### Current Database State
Existing records may have incorrect `ORIGINAL_ARCHIVE_FILE_NAME` values (pointing to nested archives instead of first-level).

### Options

**Option 1: Accept Historical Data**
- Leave existing records as-is
- Fix only applies to new processing
- Document the cutoff date

**Option 2: Clean Historical Data**
```sql
-- Set nested archive references to NULL (conservative approach)
UPDATE file_transfer_error_log
SET ORIGINAL_ARCHIVE_FILE_NAME = NULL
WHERE CREATION_DATE < '2025-10-21'  -- Before fix date
AND SOLVED = 0;
```

**Option 3: Manual Correction (if pattern is known)**
```sql
-- Example: If you know innermost.zip came from archive.tar
UPDATE file_transfer_error_log
SET ORIGINAL_ARCHIVE_FILE_NAME = 'archive.tar'
WHERE ORIGINAL_ARCHIVE_FILE_NAME = 'innermost.zip'
AND FILE_NAME LIKE '%.xml';
```

---

## Benefits

### Before Fix:
❌ Nested files tracked with immediate parent archive
❌ Difficult to trace back to original uploaded archive
❌ Inconsistent tracking depth (1-level vs 2-level vs 3-level)
❌ Wrong data in `FILES_INFO.originalZip`

### After Fix:
✅ All files track back to first-level (root) archive
✅ Easy to identify which uploaded archive caused issues
✅ Consistent tracking regardless of nesting depth
✅ Correct data in both error log and zip tracking tables
✅ Accurate reporting and auditing

---

## Summary

**Problem:** Nested archives were tracked with immediate parent instead of first-level archive

**Solution:** Added `fileToFirstLevelArchive` map to track root archive through all extraction levels

**Impact:**
- All extracted files now correctly reference the outermost archive
- Both `file_transfer_error_log.ORIGINAL_ARCHIVE_FILE_NAME` and `file_transfer_zip_tracking.FILES_INFO.originalZip` are accurate
- No matter how deeply nested, the first-level archive is always tracked

**Example:**
```
archive.tar → innermost.zip → file.xml

Before: file.xml.ORIGINAL_ARCHIVE_FILE_NAME = "innermost.zip" ❌
After:  file.xml.ORIGINAL_ARCHIVE_FILE_NAME = "archive.tar" ✅
```
