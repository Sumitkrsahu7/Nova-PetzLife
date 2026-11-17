import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface GoogleReviewDto {
  id: number;
  authorName: string;
  authorPhotoUrl?: string;
  rating: number;
  relativeTime?: string;
  reviewText: string;
  isFeatured: boolean;
  reviewTimestamp?: string;
}

export interface GoogleBusinessProfileDto {
  name: string;
  address: string;
  city: string;
  pinCode: string;
  placeId: string;
  latitude: number;
  longitude: number;
  overallRating: number;
  totalUserRatings: number;
  googleMapsUrl: string;
  phoneNumber: string;
  isOpenNow: boolean;
  openingHoursDescription: string;
  featuredReviews: GoogleReviewDto[];
}

@Injectable({
  providedIn: 'root'
})
export class GoogleBusinessService {
  private apiUrl = `${environment.apiUrl}/google-business`;

  constructor(private http: HttpClient) {}

  getProfile(): Observable<GoogleBusinessProfileDto> {
    return this.http.get<{ success: boolean; data: GoogleBusinessProfileDto }>(`${this.apiUrl}/profile`)
      .pipe(map(res => res.data));
  }

  getFeaturedReviews(): Observable<GoogleReviewDto[]> {
    return this.http.get<{ success: boolean; data: GoogleReviewDto[] }>(`${this.apiUrl}/reviews`)
      .pipe(map(res => res.data || []));
  }
}
