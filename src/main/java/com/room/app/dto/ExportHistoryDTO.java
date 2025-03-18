package com.room.app.dto;

//ExportHistoryDTO.java
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ExportHistoryDTO {
	private String exportType;
    private LocalDateTime timestamp;
    private String filename;
    private String fileSize;

    public ExportHistoryDTO(String exportType, LocalDateTime timestamp, 
                           String filename, String fileSize) {
        this.exportType = exportType;
        this.timestamp = timestamp;
        this.filename = filename;
        this.fileSize = fileSize;
    }

	public String getExportType() {
		return exportType;
	}

	public void setExportType(String exportType) {
		this.exportType = exportType;
	}

	public LocalDateTime getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(LocalDateTime timestamp) {
		this.timestamp = timestamp;
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public String getFileSize() {
		return fileSize;
	}

	public void setFileSize(String fileSize) {
		this.fileSize = fileSize;
	}

}