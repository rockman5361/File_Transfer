package org.ft.repository;

import org.ft.entity.FileTransferErrorLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileTransferErrorLogRepository extends JpaRepository<FileTransferErrorLogEntity, String> {
}
