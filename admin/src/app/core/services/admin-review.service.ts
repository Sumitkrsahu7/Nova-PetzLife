import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface AdminReviewDto {
  id: number;
  productId: number;
  productName: string;
  customerId: number;
  customerName: string;
  rating: number;
  title: string;
  comment: string;
  isVerifiedPurchase: boolean;
  status: string;
  adminReply?: string;
  imageUrls: string[];
  createdAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class AdminReviewService {
  private apiUrl = `${environment.apiUrl}/admin/reviews`;

  constructor(private http: HttpClient) {}

  getAllReviews(status: string = 'ALL'): Observable<AdminReviewDto[]> {
    return this.http.get<{ success: boolean; data: AdminReviewDto[] }>(`${this.apiUrl}?status=${status}`)
      .pipe(map(res => res.data || []));
  }

  moderateReview(reviewId: number, status: string, adminReply?: string): Observable<AdminReviewDto> {
    return this.http.patch<{ success: boolean; data: AdminReviewDto }>(`${this.apiUrl}/${reviewId}/moderate`, {
      status,
      adminReply
    }).pipe(map(res => res.data));
  }

  deleteReview(reviewId: number): Observable<void> {
    return this.http.delete<{ success: boolean }>(`${this.apiUrl}/${reviewId}`)
      .pipe(map(() => void 0));
  }
}
