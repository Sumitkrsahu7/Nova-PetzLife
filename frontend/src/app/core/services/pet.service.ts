import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface PetProfile {
  id: number;
  customerId: number;
  customerName: string;
  petName: string;
  petType: string;
  breed?: string;
  dateOfBirth?: string;
  gender?: string;
  weightKg?: number;
  lifeStage?: string;
  dietaryPreferences?: string;
  foodSensitivities?: string;
  adminFeedback?: string;
  adminFeedbackUpdatedAt?: string;
  createdAt: string;
}

export interface CreatePetPayload {
  petName: string;
  petType: string;
  breed?: string;
  dateOfBirth?: string;
  gender?: string;
  weightKg?: number;
  lifeStage?: string;
  dietaryPreferences?: string;
  foodSensitivities?: string;
}

export interface RecommendedProduct {
  id: number;
  name: string;
  brandName?: string;
  categoryName?: string;
  regularPrice: number;
  salePrice?: number;
  currentStock: number;
  isInStock: boolean;
  petType?: string;
  lifeStage?: string;
  featuredImageUrl?: string;
}

export interface PetRecommendation {
  petId: number;
  petName: string;
  petType: string;
  breed?: string;
  lifeStage?: string;
  rationale: string;
  recommendedProducts: RecommendedProduct[];
}

@Injectable({
  providedIn: 'root'
})
export class PetService {
  private apiUrl = `${environment.apiUrl}/customer/pets`;

  constructor(private http: HttpClient) {}

  public getPets(): Observable<{ success: boolean; data: PetProfile[] }> {
    return this.http.get<{ success: boolean; data: PetProfile[] }>(this.apiUrl);
  }

  public getPet(id: number): Observable<{ success: boolean; data: PetProfile }> {
    return this.http.get<{ success: boolean; data: PetProfile }>(`${this.apiUrl}/${id}`);
  }

  public createPet(payload: CreatePetPayload): Observable<{ success: boolean; data: PetProfile }> {
    return this.http.post<{ success: boolean; data: PetProfile }>(this.apiUrl, payload);
  }

  public createGuestPet(payload: CreatePetPayload): Observable<{ success: boolean; data: PetProfile }> {
    return this.http.post<{ success: boolean; data: PetProfile }>(`${this.apiUrl}/guest`, payload);
  }

  public updatePet(id: number, payload: CreatePetPayload): Observable<{ success: boolean; data: PetProfile }> {
    return this.http.put<{ success: boolean; data: PetProfile }>(`${this.apiUrl}/${id}`, payload);
  }

  public deletePet(id: number): Observable<{ success: boolean; data: null }> {
    return this.http.delete<{ success: boolean; data: null }>(`${this.apiUrl}/${id}`);
  }

  public getRecommendations(petId: number): Observable<{ success: boolean; data: PetRecommendation }> {
    return this.http.get<{ success: boolean; data: PetRecommendation }>(`${this.apiUrl}/${petId}/recommendations`);
  }
}
