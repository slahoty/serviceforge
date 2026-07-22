import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Technician } from '../models/technician.model';
import { Job, BookJobRequest } from '../models/job.model';

@Injectable({ providedIn: 'root' })
export class TechnicianService {

  private readonly baseUrl = environment.apiBaseUrl;

  constructor(private http: HttpClient) {}

  getTechnicians(): Observable<Technician[]> {
    return this.http.get<Technician[]>(`${this.baseUrl}/technicians`);
  }

  getJobsForTechnician(technicianId: number): Observable<Job[]> {
    return this.http.get<Job[]>(`${this.baseUrl}/technicians/${technicianId}/jobs`);
  }

  bookJob(request: BookJobRequest): Observable<Job> {
    return this.http.post<Job>(`${this.baseUrl}/jobs`, request);
  }
}
