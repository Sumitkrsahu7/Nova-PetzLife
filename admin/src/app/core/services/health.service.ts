import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface BackendHealthData {
  status: string;
  service: string;
  version: string;
  profile: string;
  shopLocation: string;
  timestamp: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

@Injectable({
  providedIn: 'root'
})
export class HealthService {
  private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  checkHealth(): Observable<{ online: boolean; data?: BackendHealthData; error?: string }> {
    return this.http.get<ApiResponse<BackendHealthData>>(`${this.apiUrl}/health`).pipe(
      map(response => ({
        online: response.success && response.data?.status === 'UP',
        data: response.data
      })),
      catchError(err => of({
        online: false,
        error: err.message || 'Backend service unreachable'
      }))
    );
  }
}
