import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GoogleBusinessProfileSummary, GoogleReviewItem } from '../../models/review.model';
import { GoogleBusinessService } from '../../core/services/google-business.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-google-reviews',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './google-reviews.component.html',
  styleUrls: ['./google-reviews.component.scss']
})
export class GoogleReviewsComponent implements OnInit {
  @Input() businessInfo: GoogleBusinessProfileSummary = {
    businessName: 'Petzlife',
    formattedAddress: environment.shopAddress,
    googlePlaceId: 'ChIJ7wMZeWpvCDsR_NOVA_PETZLIFE',
    overallRating: 4.8,
    totalReviewCount: 148,
    googleMapsUrl: 'https://www.google.com/search?kgmid=/g/11t30kwrpx&q=Petzlife',
    isApiConfigured: true,
    statusMessage: 'Google Business Profile synchronized'
  };

  @Input() featuredReviews: GoogleReviewItem[] = [];

  constructor(private googleBusinessService: GoogleBusinessService) { }

  ngOnInit(): void {
    this.googleBusinessService.getProfile().subscribe({
      next: (profile) => {
        if (profile) {
          this.businessInfo = {
            businessName: profile.name,
            formattedAddress: profile.address + ', ' + profile.city + ' ' + profile.pinCode,
            googlePlaceId: profile.placeId,
            overallRating: profile.overallRating,
            totalReviewCount: profile.totalUserRatings,
            googleMapsUrl: profile.googleMapsUrl,
            isApiConfigured: true,
            statusMessage: 'Synced from Google Business Profile'
          };
          if (profile.featuredReviews && profile.featuredReviews.length > 0) {
            this.featuredReviews = profile.featuredReviews.map(r => ({
              id: 'g-rev-' + r.id,
              authorName: r.authorName,
              rating: r.rating,
              relativeTime: r.relativeTime || 'recently',
              text: r.reviewText,
              sourceUrl: profile.googleMapsUrl
            }));
          }
        }
      },
      error: () => {
        // Fallback to static verified our store details if API unavailable
      }
    });
  }
}

