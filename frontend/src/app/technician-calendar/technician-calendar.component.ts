import { Component, OnInit } from '@angular/core';
import { Technician } from '../models/technician.model';
import { Job, BookJobRequest } from '../models/job.model';
import { TechnicianService } from '../services/technician.service';

@Component({
  selector: 'app-technician-calendar',
  templateUrl: './technician-calendar.component.html',
  styleUrls: ['./technician-calendar.component.css']
})
export class TechnicianCalendarComponent implements OnInit {

  technicians: Technician[] = [];
  selectedTechnicianId: number | null = null;
  jobs: Job[] = [];

  newCustomerName = '';
  newStartTime = '';
  newEndTime = '';
  errorMessage = '';

  constructor(private technicianService: TechnicianService) {}

  ngOnInit(): void {
    this.technicianService.getTechnicians().subscribe(technicians => {
      this.technicians = technicians;
      if (technicians.length > 0) {
        this.selectTechnician(technicians[0].id);
      }
    });
  }

  selectTechnician(technicianId: number): void {
    this.selectedTechnicianId = technicianId;
    this.errorMessage = '';
    this.loadJobs();
  }

  loadJobs(): void {
    if (this.selectedTechnicianId === null) {
      return;
    }
    this.technicianService.getJobsForTechnician(this.selectedTechnicianId)
      .subscribe(jobs => {
        this.jobs = jobs.sort((a, b) => a.startTime.localeCompare(b.startTime));
      });
  }

  bookJob(): void {
    if (this.selectedTechnicianId === null || !this.newCustomerName || !this.newStartTime || !this.newEndTime) {
      this.errorMessage = 'Fill in customer name, start time, and end time.';
      return;
    }

    const request: BookJobRequest = {
      technicianId: this.selectedTechnicianId,
      customerName: this.newCustomerName,
      startTime: this.newStartTime,
      endTime: this.newEndTime
    };

    this.errorMessage = '';
    this.technicianService.bookJob(request).subscribe({
      next: () => {
        this.newCustomerName = '';
        this.newStartTime = '';
        this.newEndTime = '';
        this.loadJobs();
      },
      error: (err) => {
        this.errorMessage = err?.error?.message || 'Could not book this job.';
      }
    });
  }
}
