package com.serviceforge.controller;

import com.serviceforge.dto.ApiError;
import com.serviceforge.dto.BookJobRequest;
import com.serviceforge.model.Job;
import com.serviceforge.service.TechnicianAvailabilityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final TechnicianAvailabilityService availabilityService;

    public JobController(TechnicianAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @PostMapping
    public ResponseEntity<?> bookJob(@Valid @RequestBody BookJobRequest request) {
        try {
            Job job = availabilityService.bookJob(
                    request.getTechnicianId(),
                    request.getCustomerName(),
                    request.getStartTime(),
                    request.getEndTime());
            return ResponseEntity.status(201).body(job);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ApiError(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(new ApiError(e.getMessage()));
        }
    }
}
