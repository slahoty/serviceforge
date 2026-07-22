package com.serviceforge.controller;

import com.serviceforge.dto.ApiError;
import com.serviceforge.model.Job;
import com.serviceforge.model.Technician;
import com.serviceforge.service.TechnicianAvailabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/technicians")
public class TechnicianController {

    private final TechnicianAvailabilityService availabilityService;

    public TechnicianController(TechnicianAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public ResponseEntity<List<Technician>> getAllTechnicians() {
        return ResponseEntity.ok(availabilityService.getAllTechnicians());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTechnician(@PathVariable Long id) {
        return availabilityService.findTechnician(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(new ApiError("No technician with id " + id)));
    }

    @GetMapping("/{id}/jobs")
    public ResponseEntity<?> getJobsForTechnician(@PathVariable Long id) {
        if (availabilityService.findTechnician(id).isEmpty()) {
            return ResponseEntity.status(404).body(new ApiError("No technician with id " + id));
        }
        List<Job> jobs = availabilityService.getJobsForTechnician(id);
        return ResponseEntity.ok(jobs);
    }
}
