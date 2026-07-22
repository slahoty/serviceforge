package com.serviceforge.model;

import java.time.LocalDateTime;

public class Job {

    private final Long id;
    private final Long technicianId;
    private final String customerName;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private JobStatus status;

    public Job(Long id, Long technicianId, String customerName,
               LocalDateTime startTime, LocalDateTime endTime, JobStatus status) {
        this.id = id;
        this.technicianId = technicianId;
        this.customerName = customerName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getTechnicianId() {
        return technicianId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }
}
