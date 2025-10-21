# Compilation Fix - FileTrackingContext

## Issue
Compilation error when building the project:
```
java: cannot find symbol
  symbol:   method getFolderPath(java.lang.String)
  location: variable trackingContext of type org.ft.util.FileTrackingContext
```

## Root Cause
The `@Data` Lombok annotation on `FileTrackingContext` was auto-generating getters for all fields. This could have caused conflicts with the manually defined `getFolderPath()` and `getOriginalArchiveName()` methods.

## Solution
Replaced `@Data` with `@Getter` on only the `environment` field that needs it.

### Before:
```java
import lombok.Data;

@Data
public class FileTrackingContext {
    private final String environment;
    // ... other fields
```

### After:
```java
import lombok.Getter;

public class FileTrackingContext {
    @Getter
    private final String environment;
    // ... other fields
```

## Manual Methods
The following methods are now explicitly defined and will not conflict:
- `getFolderPath(String fileName)` - Returns folder path from `fileToFolderPath` map
- `getOriginalArchiveName(String fileName)` - Returns archive name from `extractedFileToZip` map
- `getFileInfo(String fileName)` - Returns FileInfoDTO from `fileMetadata` map
- `getAllSourceFolderPaths()` - Returns set of source folder paths
- `getTrackedFileCount()` - Returns count of tracked files

## Verification
After this fix, the project should compile successfully. To verify:

```bash
# Clean and rebuild
mvn clean compile

# Or if using IDE, rebuild the project
```

## Related Files
- `src/main/java/org/ft/util/FileTrackingContext.java` - Fixed class
- `src/main/java/org/ft/services/FileProcessorService.java` - Uses the methods

## Prevention
When using Lombok annotations:
- Use `@Getter` / `@Setter` on specific fields instead of `@Data` on the class when you have custom methods
- `@Data` is equivalent to `@Getter` + `@Setter` + `@ToString` + `@EqualsAndHashCode` + `@RequiredArgsConstructor`
- Avoid mixing Lombok-generated and manually written methods with the same names
