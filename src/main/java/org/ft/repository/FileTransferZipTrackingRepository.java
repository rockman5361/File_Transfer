package org.ft.repository;

import org.ft.entity.FileTransferZipTrackingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for FileTransferZipTracking entity operations.
 */
@Repository
public interface FileTransferZipTrackingRepository extends JpaRepository<FileTransferZipTrackingEntity, Long> {

    /**
     * Find all tracking records by data source
     */
    @Query("SELECT e FROM FileTransferZipTrackingEntity e WHERE e.DATA_SOURCE = :dataSource")
    List<FileTransferZipTrackingEntity> findByDataSource(@Param("dataSource") String dataSource);

    /**
     * Find all tracking records by data source and environment
     */
    @Query("SELECT e FROM FileTransferZipTrackingEntity e WHERE e.DATA_SOURCE = :dataSource AND e.ENVIRONMENT = :environment")
    List<FileTransferZipTrackingEntity> findByDataSourceAndEnvironment(@Param("dataSource") String dataSource, @Param("environment") String environment);

    /**
     * Find tracking records created after a specific timestamp
     */
    @Query("SELECT e FROM FileTransferZipTrackingEntity e WHERE e.CREATED_TIMESTAMP > :timestamp")
    List<FileTransferZipTrackingEntity> findByCreatedTimestampAfter(@Param("timestamp") LocalDateTime timestamp);

    /**
     * Find by final zip name
     */
    @Query("SELECT e FROM FileTransferZipTrackingEntity e WHERE e.FINAL_ZIP_NAME = :finalZipName")
    FileTransferZipTrackingEntity findByFinalZipName(@Param("finalZipName") String finalZipName);
}
