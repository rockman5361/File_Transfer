package org.ft.constants;

/**
 * Constants for file operations
 */
public final class FileConstants {

    private FileConstants() {
        // Prevent instantiation
    }

    // File extensions
    public static final String ZIP_EXTENSION = ".zip";
    public static final String TAR_EXTENSION = ".tar";
    public static final String TAR_GZ_EXTENSION = ".tar.gz";
    public static final String TZ_EXTENSION = ".tz";
    public static final String SEVEN_Z_EXTENSION = ".7z";
    public static final String XML_EXTENSION = ".xml";
    public static final String TXT_EXTENSION = ".txt";
    public static final String GZ_EXTENSION = ".gz";

    // Folder names
    public static final String TEMP_FOLDER = "temp";
    public static final String BACKUP_FOLDER = "backup";
    public static final String LOG_FOLDER = "log";
    public static final String ERROR_FOLDER = "error";
    public static final String FILES_FOLDER = "files";

    // Buffer sizes
    public static final int BUFFER_SIZE = 1024;
    public static final int DEFAULT_MAX_ZIP_SIZE_MB = 1024 * 1024;

    // Date/Time formats
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATETIME_FORMAT = "yyyy:MM:dd HH:mm:ss";
    public static final String FILE_DATETIME_FORMAT = "yyyyMMdd'T'HHmmss";

    // Sleep duration
    public static final long ZIP_CLOSE_SLEEP_MS = 1000L;

    public static final String FILE_ALREADY_EXISTS = "already exists";
}
