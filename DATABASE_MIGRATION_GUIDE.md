# Database Migration Guide - Error Log Table Refactoring

## Quick Start

### Prerequisites
- MySQL client installed
- Access to `file_transfer_db` database
- Database backup completed

### Migration Steps

#### 1. Backup Current Data (IMPORTANT!)
```bash
# Backup the entire database
mysqldump -u root -p file_transfer_db > backup_file_transfer_db_$(date +%Y%m%d_%H%M%S).sql

# Or backup just the error log table
mysqldump -u root -p file_transfer_db file_transfer_error_log > backup_error_log_$(date +%Y%m%d_%H%M%S).sql
```

#### 2. Run Migration Script
```bash
# Navigate to project directory
cd D:\Backend\File_Transfer

# Execute migration
mysql -u root -p file_transfer_db < src/main/resources/db/migration/alter_error_log_table.sql
```

#### 3. Verify Migration
```sql
-- Connect to database
mysql -u root -p file_transfer_db

-- Check table structure
DESCRIBE file_transfer_error_log;

-- Expected output should show:
-- - FOLDER_PATH (TEXT)
-- - ORIGINAL_ARCHIVE_FILE_NAME (VARCHAR 500)
-- - FILE_LIST column should be REMOVED

-- Check existing data
SELECT COUNT(*) FROM file_transfer_error_log;

-- Verify new columns exist and are NULL for old records
SELECT ID, FILE_NAME, FOLDER_PATH, ORIGINAL_ARCHIVE_FILE_NAME
FROM file_transfer_error_log
LIMIT 5;
```

---

## Detailed Migration Script

The migration script performs the following operations:

```sql
-- Add new columns
ALTER TABLE file_transfer_error_log
ADD COLUMN FOLDER_PATH TEXT NULL AFTER FILE_NAME,
ADD COLUMN ORIGINAL_ARCHIVE_FILE_NAME VARCHAR(500) NULL AFTER FOLDER_PATH;

-- Drop old column
ALTER TABLE file_transfer_error_log
DROP COLUMN FILE_LIST;
```

---

## Rollback Plan

If you need to rollback the migration:

### Option 1: Restore from Backup
```bash
# Restore entire database
mysql -u root -p file_transfer_db < backup_file_transfer_db_YYYYMMDD_HHMMSS.sql

# Or restore just the error log table
mysql -u root -p file_transfer_db < backup_error_log_YYYYMMDD_HHMMSS.sql
```

### Option 2: Manual Rollback
```sql
-- Add back FILE_LIST column
ALTER TABLE file_transfer_error_log
ADD COLUMN FILE_LIST TEXT NULL AFTER FILE_NAME;

-- Remove new columns
ALTER TABLE file_transfer_error_log
DROP COLUMN FOLDER_PATH,
DROP COLUMN ORIGINAL_ARCHIVE_FILE_NAME;
```

**Note:** If you rollback, you'll also need to revert the application code to the previous version.

---

## Data Migration Considerations

### Existing Records
- Old records will have `FOLDER_PATH` and `ORIGINAL_ARCHIVE_FILE_NAME` set to NULL
- This is expected behavior
- New error logs will populate these fields

### No Data Migration Required
The old `FILE_LIST` column is dropped without data migration because:
1. Error logs are typically transient (solved and archived)
2. The new approach creates separate rows per file
3. Historical error data is preserved in backups

### If You Need to Preserve FILE_LIST Data
If you need to keep the old FILE_LIST data before dropping the column:

```sql
-- Create a backup table with old data
CREATE TABLE file_transfer_error_log_backup AS
SELECT * FROM file_transfer_error_log;

-- Then proceed with the migration
-- You can query the backup table if needed:
SELECT * FROM file_transfer_error_log_backup WHERE ID = 'FTEL123456789';
```

---

## Verification Checklist

After migration, verify:

- [ ] Table structure updated correctly
  ```sql
  DESCRIBE file_transfer_error_log;
  ```

- [ ] New columns exist
  ```sql
  SHOW COLUMNS FROM file_transfer_error_log LIKE 'FOLDER_PATH';
  SHOW COLUMNS FROM file_transfer_error_log LIKE 'ORIGINAL_ARCHIVE_FILE_NAME';
  ```

- [ ] Old column removed
  ```sql
  -- This should return empty result
  SHOW COLUMNS FROM file_transfer_error_log LIKE 'FILE_LIST';
  ```

- [ ] Existing data intact
  ```sql
  SELECT COUNT(*) FROM file_transfer_error_log;
  -- Count should match pre-migration count
  ```

- [ ] Application can connect and insert new records
  - Deploy updated application
  - Trigger an error scenario
  - Verify new record has FOLDER_PATH and ORIGINAL_ARCHIVE_FILE_NAME populated

---

## Testing the Migration

### Test Scenario 1: Insert New Error Log
```sql
-- Manually insert a test record
INSERT INTO file_transfer_error_log (
    ID, ENVIRONMENT, DATA_SOURCE, ERROR_MESSAGE, FILE_NAME,
    FOLDER_PATH, ORIGINAL_ARCHIVE_FILE_NAME, SOLVED
) VALUES (
    'FTEL_TEST_001',
    'stage',
    'TEST_SOURCE',
    'DUPLICATE_FILE',
    'test_document.xml',
    'D:\\Backend\\TEST',
    'test_archive.zip',
    0
);

-- Verify insert
SELECT * FROM file_transfer_error_log WHERE ID = 'FTEL_TEST_001';

-- Cleanup
DELETE FROM file_transfer_error_log WHERE ID = 'FTEL_TEST_001';
```

### Test Scenario 2: Query by New Columns
```sql
-- Query by folder path
SELECT * FROM file_transfer_error_log
WHERE FOLDER_PATH LIKE '%TEST%';

-- Query by archive name
SELECT * FROM file_transfer_error_log
WHERE ORIGINAL_ARCHIVE_FILE_NAME IS NOT NULL;

-- Count errors by folder
SELECT FOLDER_PATH, COUNT(*) as error_count
FROM file_transfer_error_log
GROUP BY FOLDER_PATH;

-- Count errors by archive
SELECT ORIGINAL_ARCHIVE_FILE_NAME, COUNT(*) as error_count
FROM file_transfer_error_log
WHERE ORIGINAL_ARCHIVE_FILE_NAME IS NOT NULL
GROUP BY ORIGINAL_ARCHIVE_FILE_NAME;
```

---

## Production Deployment Checklist

### Pre-Deployment
- [ ] Database backup completed
- [ ] Migration script tested in development environment
- [ ] Migration script tested in staging environment
- [ ] Rollback plan tested
- [ ] Downtime window scheduled (if needed)
- [ ] Stakeholders notified

### During Deployment
- [ ] Stop application
- [ ] Run database migration
- [ ] Verify migration success
- [ ] Deploy updated application code
- [ ] Start application
- [ ] Verify application startup

### Post-Deployment
- [ ] Run verification queries
- [ ] Test error logging functionality
- [ ] Monitor application logs for errors
- [ ] Verify new error records have required fields populated
- [ ] Document actual downtime (if any)
- [ ] Notify stakeholders of completion

---

## Troubleshooting

### Issue: Migration fails with "Unknown column"
**Cause:** FILE_LIST column doesn't exist (already removed)

**Solution:**
```sql
-- Check if column exists
SHOW COLUMNS FROM file_transfer_error_log LIKE 'FILE_LIST';

-- If it doesn't exist, skip the DROP statement
ALTER TABLE file_transfer_error_log
ADD COLUMN FOLDER_PATH TEXT NULL AFTER FILE_NAME,
ADD COLUMN ORIGINAL_ARCHIVE_FILE_NAME VARCHAR(500) NULL AFTER FOLDER_PATH;
```

### Issue: Migration fails with "Duplicate column name"
**Cause:** New columns already exist

**Solution:**
```sql
-- Check existing columns
DESCRIBE file_transfer_error_log;

-- If FOLDER_PATH and ORIGINAL_ARCHIVE_FILE_NAME exist, only drop FILE_LIST
ALTER TABLE file_transfer_error_log DROP COLUMN FILE_LIST;
```

### Issue: Application errors after migration
**Cause:** Application code not updated

**Solution:**
1. Verify you deployed the updated application code
2. Check application logs for specific errors
3. Ensure `FileTransferErrorLogEntity.java` has been updated
4. Restart application

### Issue: New error logs missing FOLDER_PATH or ORIGINAL_ARCHIVE_FILE_NAME
**Cause:** Tracking context not properly passing data

**Solution:**
1. Check `FileTrackingContext` is tracking files correctly
2. Verify `trackDirectFile()` and `trackExtractedFile()` are called
3. Check application logs for warnings
4. Verify source folder paths are configured correctly in `file_transfer_folder_path` table

---

## Performance Considerations

The migration:
- **Does not require table locks** (uses ALTER TABLE which may lock briefly)
- **Does not migrate data** (just schema changes)
- **Should complete quickly** (< 1 second for small tables, < 1 minute for large tables)
- **Does not affect running application** if done during maintenance window

### Estimated Downtime
- **Small tables** (< 10,000 rows): < 5 seconds
- **Medium tables** (10,000 - 100,000 rows): < 30 seconds
- **Large tables** (> 100,000 rows): < 2 minutes

**Recommendation:** Schedule during low-traffic period to minimize impact

---

## Support

If you encounter issues during migration:
1. Do NOT proceed with application deployment if migration fails
2. Review error messages from MySQL
3. Check database logs: `SHOW ENGINE INNODB STATUS;`
4. Contact database administrator or development team
5. If needed, execute rollback plan

---

## Post-Migration Monitoring

### Key Metrics to Monitor
1. **Error log insertion rate**
   ```sql
   SELECT DATE(CREATION_DATE) as date, COUNT(*) as count
   FROM file_transfer_error_log
   WHERE CREATION_DATE >= CURDATE() - INTERVAL 7 DAY
   GROUP BY DATE(CREATION_DATE);
   ```

2. **NULL folder paths** (should decrease over time)
   ```sql
   SELECT COUNT(*) as null_folder_paths
   FROM file_transfer_error_log
   WHERE FOLDER_PATH IS NULL AND CREATION_DATE >= CURDATE();
   ```

3. **Archive extraction errors**
   ```sql
   SELECT ORIGINAL_ARCHIVE_FILE_NAME, COUNT(*) as error_count
   FROM file_transfer_error_log
   WHERE ERROR_MESSAGE = 'EXTRACTION_ERROR'
   AND CREATION_DATE >= CURDATE() - INTERVAL 7 DAY
   GROUP BY ORIGINAL_ARCHIVE_FILE_NAME;
   ```

---

## References

- Main Schema: `create_tables.sql`
- Migration Script: `src/main/resources/db/migration/alter_error_log_table.sql`
- Refactoring Summary: `REFACTORING_SUMMARY.md`
- Entity Class: `src/main/java/org/ft/entity/FileTransferErrorLogEntity.java`
