package org.ft.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.ft.entity.FileTransferErrorLogEntity;
import org.ft.entity.FileTransferFolderPathEntity;
import org.ft.entity.FileTransferDataSourceEntity;
import org.ft.entity.FileTransferSettingEntity;

import java.util.List;

@Mapper
public interface CustomMapper {

    @Select("SELECT * FROM FILE_TRANSFER_DATA_SOURCE WHERE ACTIVE = 1")
    List<FileTransferDataSourceEntity> getFileTransferDataSources();

    @Select("SELECT * FROM FILE_TRANSFER_FOLDER_PATH WHERE DATA_SOURCE_ID = #{dataSource} AND ACTIVE = 1")
    List<FileTransferFolderPathEntity> getFileTransferFolderPathByDataSourceId(int dataSource);

    @Select("SELECT * FROM FILE_TRANSFER_SETTING WHERE TYPE = #{type} LIMIT 1")
    FileTransferSettingEntity getFileTransferSettingByType(String type);

    @Select("SELECT * FROM FILE_TRANSFER_ERROR_LOG WHERE FILE_NAME = #{fileName} AND DATA_SOURCE = #{dataSource} AND ENVIRONMENT = #{environment} AND ERROR_MESSAGE = #{errorMessage} AND SOLVED = 0 LIMIT 1")
    FileTransferErrorLogEntity getFileTransferErrorLog(String fileName, String dataSource, String environment, String errorMessage);

    @Select("SELECT COUNT(*) FROM FILE_TRANSFER_ERROR_LOG WHERE ID = #{id} AND SOLVED = 0")
    boolean isExistFileTransferErrorLogId(String id);
}
