export type JobStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';

export interface Job {
  id: number;
  technicianId: number;
  customerName: string;
  startTime: string;  // ISO local date-time string, e.g. "2026-07-10T09:00:00"
  endTime: string;
  status: JobStatus;
}

export interface BookJobRequest {
  technicianId: number;
  customerName: string;
  startTime: string;
  endTime: string;
}
