import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { PetService, PetProfile, CreatePetPayload, PetRecommendation, RecommendedProduct } from '../../core/services/pet.service';
import { CartService } from '../../core/services/cart.service';
import { BackButtonComponent } from '../../components/back-button/back-button.component';

@Component({
  selector: 'app-pets',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, BackButtonComponent],
  templateUrl: './pets.component.html',
  styleUrls: ['./pets.component.scss']
})
export class PetsComponent implements OnInit {
  pets: PetProfile[] = [];
  selectedPet: PetProfile | null = null;
  recommendations: PetRecommendation | null = null;

  isLoading = false;
  isLoadingRecommendations = false;
  isSaving = false;
  showForm = false;
  editingPetId: number | null = null;
  errorMessage = '';
  modalErrorMessage = '';
  successMessage = '';

  // Form Model
  petForm: CreatePetPayload = {
    petName: '',
    petType: 'Dog',
    breed: '',
    dateOfBirth: '',
    gender: 'Male',
    weightKg: undefined,
    lifeStage: 'Adult',
    dietaryPreferences: '',
    foodSensitivities: ''
  };

  petTypes = ['Dog', 'Cat', 'Bird', 'Fish', 'Small Animal'];
  lifeStages = ['Puppy', 'Kitten', 'Adult', 'Senior'];
  genders = ['Male', 'Female'];

  constructor(
    private petService: PetService,
    private cartService: CartService
  ) {}

  ngOnInit(): void {
    this.loadPets();
  }

  loadPets(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.petService.getPets().subscribe({
      next: res => {
        this.isLoading = false;
        if (res?.success && res.data) {
          this.pets = res.data;
          if (this.pets.length > 0 && !this.selectedPet) {
            this.selectPetForRecommendations(this.pets[0]);
          }
        }
      },
      error: err => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Could not load pet profiles.';
      }
    });
  }

  openNewPetForm(): void {
    this.editingPetId = null;
    this.petForm = {
      petName: '',
      petType: 'Dog',
      breed: '',
      dateOfBirth: '',
      gender: 'Male',
      weightKg: undefined,
      lifeStage: 'Adult',
      dietaryPreferences: '',
      foodSensitivities: ''
    };
    this.showForm = true;
    this.successMessage = '';
    this.errorMessage = '';
    this.modalErrorMessage = '';
  }

  editPet(pet: PetProfile): void {
    this.editingPetId = pet.id;
    this.petForm = {
      petName: pet.petName,
      petType: pet.petType,
      breed: pet.breed || '',
      dateOfBirth: pet.dateOfBirth || '',
      gender: pet.gender || 'Male',
      weightKg: pet.weightKg,
      lifeStage: pet.lifeStage || 'Adult',
      dietaryPreferences: pet.dietaryPreferences || '',
      foodSensitivities: pet.foodSensitivities || ''
    };
    this.showForm = true;
    this.successMessage = '';
    this.errorMessage = '';
    this.modalErrorMessage = '';
  }

  cancelForm(): void {
    this.showForm = false;
    this.editingPetId = null;
    this.modalErrorMessage = '';
  }

  savePet(): void {
    if (!this.petForm.petName.trim() || !this.petForm.petType) {
      this.modalErrorMessage = 'Pet name and pet species are required.';
      return;
    }

    this.isSaving = true;
    this.errorMessage = '';
    this.modalErrorMessage = '';
    this.successMessage = '';

    const action = this.editingPetId
      ? this.petService.updatePet(this.editingPetId, this.petForm)
      : this.petService.createPet(this.petForm);

    action.subscribe({
      next: res => {
        this.isSaving = false;
        this.showForm = false;
        this.modalErrorMessage = '';
        this.successMessage = this.editingPetId ? 'Pet profile updated successfully!' : 'New pet profile registered!';
        this.loadPets();
      },
      error: err => {
        this.isSaving = false;
        this.modalErrorMessage = err.error?.message || 'Failed to save pet profile. Please check the details and try again.';
      }
    });
  }

  deletePet(pet: PetProfile): void {
    if (!confirm(`Are you sure you want to remove ${pet.petName}'s profile?`)) {
      return;
    }
    this.petService.deletePet(pet.id).subscribe({
      next: () => {
        this.successMessage = `${pet.petName}'s profile removed.`;
        if (this.selectedPet?.id === pet.id) {
          this.selectedPet = null;
          this.recommendations = null;
        }
        this.loadPets();
      },
      error: err => {
        this.errorMessage = err.error?.message || 'Failed to delete pet profile.';
      }
    });
  }

  selectPetForRecommendations(pet: PetProfile): void {
    this.selectedPet = pet;
    this.isLoadingRecommendations = true;
    this.recommendations = null;

    this.petService.getRecommendations(pet.id).subscribe({
      next: res => {
        this.isLoadingRecommendations = false;
        if (res?.success && res.data) {
          this.recommendations = res.data;
        }
      },
      error: () => {
        this.isLoadingRecommendations = false;
      }
    });
  }

  addToCart(product: RecommendedProduct): void {
    const price = product.salePrice || product.regularPrice;
    this.cartService.addItem(product.id, undefined, 1, {
      productName: product.name,
      brandName: product.brandName || 'Nova Petzlife',
      primaryImageUrl: product.featuredImageUrl,
      unitPrice: price,
      availableStock: product.currentStock || 15,
      returnAllowed: true,
      returnWindowDays: 7
    }).subscribe({
      next: () => {
        this.successMessage = `Added ${product.name} to cart!`;
        setTimeout(() => this.successMessage = '', 3500);
      },
      error: () => {
        this.errorMessage = 'Failed to add item to cart.';
      }
    });
  }

  getSpeciesIcon(type: string): string {
    switch (type.toLowerCase()) {
      case 'dog': return '🐶';
      case 'cat': return '🐱';
      case 'bird': return '🦜';
      case 'fish': return '🐠';
      case 'small animal': return '🐰';
      default: return '🐾';
    }
  }
}
