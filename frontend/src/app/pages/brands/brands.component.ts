import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { BackButtonComponent } from '../../components/back-button/back-button.component';

export interface BrandItem {
  id?: number;
  name: string;
  slug: string;
  description?: string;
  logoUrl?: string;
  isActive?: boolean;
}

@Component({
  selector: 'app-brands',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, BackButtonComponent],
  templateUrl: './brands.component.html',
  styleUrls: ['./brands.component.scss']
})
export class BrandsComponent implements OnInit {
  brands: BrandItem[] = [];
  filteredBrands: BrandItem[] = [];
  searchQuery: string = '';
  isLoading: boolean = true;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadBrands();
  }

  loadBrands(): void {
    this.isLoading = true;
    this.http.get<any>(`${environment.apiUrl}/brands`).subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data && res.data.length > 0) {
          this.brands = res.data;
        } else {
          this.brands = this.getFallbackBrands();
        }
        this.filterBrands();
      },
      error: () => {
        this.isLoading = false;
        this.brands = this.getFallbackBrands();
        this.filterBrands();
      }
    });
  }

  filterBrands(): void {
    const q = this.searchQuery.trim().toLowerCase();
    if (!q) {
      this.filteredBrands = [...this.brands];
    } else {
      this.filteredBrands = this.brands.filter(b =>
        b.name.toLowerCase().includes(q) || (b.description && b.description.toLowerCase().includes(q))
      );
    }
  }

  getFallbackBrands(): BrandItem[] {
    return [
      {
        id: 1,
        name: 'Royal Canin',
        slug: 'royal-canin',
        description: 'Breed-specific science nutrition for cats and dogs with veterinary-backed formulas.',
        isActive: true
      },
      {
        id: 2,
        name: 'Farmina N&D',
        slug: 'farmina-nd',
        description: 'Italian natural and delicious grain-free & low ancestral grain formulas for carnivores.',
        isActive: true
      },
      {
        id: 3,
        name: 'Pedigree',
        slug: 'pedigree',
        description: 'Balanced daily dog nutrition with wholesome grains and animal protein essentials.',
        isActive: true
      },
      {
        id: 4,
        name: 'Whiskas',
        slug: 'whiskas',
        description: 'Tempting wet pouches and nutrient-packed dry kibbles designed specifically for cats.',
        isActive: true
      },
      {
        id: 5,
        name: 'Drools',
        slug: 'drools',
        description: 'Real chicken and egg formulas fortified with DHA, vitamins, and organic minerals.',
        isActive: true
      },
      {
        id: 6,
        name: 'Sheba',
        slug: 'sheba',
        description: 'Gourmet fine wet cat food recipes crafted with high-moisture fish fillets and tender meat.',
        isActive: true
      },
      {
        id: 7,
        name: 'Himalaya',
        slug: 'himalaya',
        description: 'Herbal pet health care, anti-tick shampoos, digestive drops, and wellness tonics.',
        isActive: true
      },
      {
        id: 8,
        name: 'Arden Grange',
        slug: 'arden-grange',
        description: 'Naturally hypoallergenic super-premium pet food made with wholesome British ingredients.',
        isActive: true
      }
    ];
  }
}
