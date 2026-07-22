package com.serviceforge.data;

import com.serviceforge.model.Job;
import com.serviceforge.model.JobStatus;
import com.serviceforge.model.Technician;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory mock data store. There is no real database in this repo (see AGENTS.md) —
 * everything here resets on restart, which is expected for a training boilerplate.
 */
@Component
public class MockDataStore {

    private final List<Technician> technicians = new ArrayList<>();
    private final List<Job> jobs = new ArrayList<>();
    private final AtomicLong jobIdSequence = new AtomicLong(1);

    @PostConstruct
    public void seed() {
        technicians.add(new Technician(1L, "Jordan Reyes", "North"));
        technicians.add(new Technician(2L, "Priya Nair", "South"));
        technicians.add(new Technician(3L, "Sam Okafor", "East"));

        // Seed a couple of non-overlapping jobs so the calendar isn't empty on first run.
        jobs.add(new Job(nextJobId(), 1L, "Acme Corp",
                LocalDateTime.now().withHour(9).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().withHour(11).withMinute(0).withSecond(0).withNano(0),
                JobStatus.SCHEDULED));
        jobs.add(new Job(nextJobId(), 1L, "Northwind Traders",
                LocalDateTime.now().withHour(14).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().withHour(15).withMinute(30).withSecond(0).withNano(0),
                JobStatus.SCHEDULED));
        jobs.add(new Job(nextJobId(), 2L, "Globex", LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().withHour(12).withMinute(0).withSecond(0).withNano(0),
                JobStatus.SCHEDULED));
    }

    public List<Technician> getAllTechnicians() {
        return Collections.unmodifiableList(technicians);
    }

    public Optional<Technician> findTechnician(Long id) {
        return technicians.stream().filter(t -> t.getId().equals(id)).findFirst();
    }

    public List<Job> getJobsForTechnician(Long technicianId) {
        return jobs.stream()
                .filter(j -> j.getTechnicianId().equals(technicianId))
                .collect(Collectors.toList());
    }

    public List<Job> getAllJobs() {
        return Collections.unmodifiableList(jobs);
    }

    public Job save(Job job) {
        jobs.add(job);
        return job;
    }

    public Long nextJobId() {
        return jobIdSequence.getAndIncrement();
    }
}
