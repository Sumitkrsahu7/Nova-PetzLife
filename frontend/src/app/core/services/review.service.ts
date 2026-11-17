import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface ReviewDto {
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

export interface ReviewSummaryDto {
  productId: number;
  averageRating: number;
  totalReviews: number;
  ratingDistribution: { [key: number]: number };
}

export interface CreateReviewRequest {
  productId: number;
  rating: number;
  title?: string;
  comment: string;
  imageUrls?: string[];
}

@Injectable({
  providedIn: 'root'
})
export class ReviewService {
  private apiUrl = `${environment.apiUrl}/reviews`;

  constructor(private http: HttpClient) {}

  getProductReviews(productId: number): Observable<ReviewDto[]> {
    return this.http.get<{ success: boolean; data: ReviewDto[] }>(`${this.apiUrl}/product/${productId}`)
      .pipe(map(res => res.data || []));
  }

  getProductReviewSummary(productId: number): Observable<ReviewSummaryDto> {
    return this.http.get<{ success: boolean; data: ReviewSummaryDto }>(`${this.apiUrl}/product/${productId}/summary`)
      .pipe(map(res => res.data));
  }

  submitReview(req: CreateReviewRequest, customerId?: number): Observable<ReviewDto> {
    const url = customerId ? `${this.apiUrl}?customerId=${customerId}` : this.apiUrl;
    return this.http.post<{ success: boolean; data: ReviewDto }>(url, req)
      .pipe(map(res => res.data));
  }
}
