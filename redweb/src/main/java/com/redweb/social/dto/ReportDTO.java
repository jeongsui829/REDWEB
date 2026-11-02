package com.redweb.social.dto;

import java.time.LocalDateTime;

public class ReportDTO {
    private Long reportId;
    private Long reporterUserId;
    private Long targetUserId;
    private String reporterNickname;
    private String targetNickname;
    private String reason;
    private String status;
    private String processedByNickname;
    private String penaltySummary;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getReporterUserId() { return reporterUserId; }
    public void setReporterUserId(Long reporterUserId) { this.reporterUserId = reporterUserId; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public String getReporterNickname() { return reporterNickname; }
    public void setReporterNickname(String reporterNickname) { this.reporterNickname = reporterNickname; }
    public String getTargetNickname() { return targetNickname; }
    public void setTargetNickname(String targetNickname) { this.targetNickname = targetNickname; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProcessedByNickname() { return processedByNickname; }
    public void setProcessedByNickname(String processedByNickname) { this.processedByNickname = processedByNickname; }
    public String getPenaltySummary() { return penaltySummary; }
    public void setPenaltySummary(String penaltySummary) { this.penaltySummary = penaltySummary; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
