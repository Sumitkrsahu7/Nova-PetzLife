export interface GoogleReviewItem {
  id: string;
  authorName: string;
  authorPhotoUrl?: string;
  rating: number;
  relativeTime: string;
  text: string;
  sourceUrl: string;
}

export interface GoogleBusinessProfileSummary {
  businessName: string;
  formattedAddress: string;
  googlePlaceId?: string;
  overallRating: number;
  totalReviewCount: number;
  googleMapsUrl: string;
  isApiConfigured: boolean;
  statusMessage: string;
}
