import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface AdminGoogleReviewDto {
  id: number;
  authorName: string;
  authorPhotoUrl?: string;
  rating: number;
  relativeTime?: string;
  reviewText: string;
  isFeatured: boolean;
  reviewTimestamp?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AdminGoogleService {
  private apiUrl = `${environment.apiUrl}/admin/google-business`;

  constructor(private http: HttpClient) {}

  getAllReviews(): Observable<AdminGoogleReviewDto[]> {
    return this.http.get<{ success: boolean; data: AdminGoogleReviewDto[] }>(`${this.apiUrl}/reviews`)
      .pipe(map(res => res.data || []));
  }

  syncReviews(): Observable<{ syncedCount: number }> {
    return this.http.post<{ success: boolean; data: { syncedCount: number } }>(`${this.apiUrl}/sync`, {})
      .pipe(map(res => res.data));
  }

  toggleFeatured(reviewId: number, isFeatured: boolean): Observable<AdminGoogleReviewDto> {
    return this.http.patch<{ success: boolean; data: AdminGoogleReviewDto }>(
      `${this.apiUrl}/reviews/${reviewId}/feature?isFeatured=${isFeatured}`,
      {}
    ).pipe(map(res => res.data));
  }
}
