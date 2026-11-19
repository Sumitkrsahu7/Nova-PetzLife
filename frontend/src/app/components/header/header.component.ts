import { Component, Input, Output, EventEmitter, OnInit, OnDestroy, HostListener, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, filter } from 'rxjs/operators';
import { CartService } from '../../core/services/cart.service';
import { AuthService } from '../../core/services/auth.service';
import { environment } from '../../../environments/environment';

export interface ProductSuggestion {
  id: number;
  name: string;
  slug: string;
  brandName?: string;
  categoryName?: string;
  price?: number;
  imageUrl?: string;
  sku?: string;
}

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent implements OnInit, OnDestroy {
  @Input() cartCount: number = 0;
  @Output() searchSubmitted = new EventEmitter<string>();

  searchQuery: string = '';
  suggestions: ProductSuggestion[] = [];
  showSuggestions: boolean = false;
  isSearching: boolean = false;
  selectedSuggestionIndex: number = -1;

  isMobileMenuOpen: boolean = false;
  isHelpDropdownOpen: boolean = false;
  showLogoutConfirm: boolean = false;

  private searchSubject = new Subject<string>();
  private searchSub?: Subscription;

  constructor(
    private cartService: CartService,
    public authService: AuthService,
    private router: Router,
    private http: HttpClient,
    private elementRef: ElementRef
  ) {}

  ngOnInit(): void {
    this.cartService.cart$.subscribe(cart => {
      if (cart) {
        this.cartCount = cart.totalItems;
      }
    });

    // Debounced autocomplete search (300ms)
    this.searchSub = this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(query => {
        if (!query || query.trim().length < 2) {
          this.suggestions = [];
          this.showSuggestions = false;
          this.isSearching = false;
          return [];
        }
        this.isSearching = true;
        return this.http.get<any>(`${environment.apiUrl}/products/suggestions?query=${encodeURIComponent(query.trim())}`);
      })
    ).subscribe({
      next: (res) => {
        this.isSearching = false;
        if (res?.success && Array.isArray(res.data)) {
          this.suggestions = res.data;
          this.showSuggestions = this.suggestions.length > 0;
          this.selectedSuggestionIndex = -1;
        } else if (Array.isArray(res)) {
          this.suggestions = res;
          this.showSuggestions = this.suggestions.length > 0;
          this.selectedSuggestionIndex = -1;
        } else {
          this.suggestions = [];
          this.showSuggestions = false;
        }
      },
      error: () => {
        this.isSearching = false;
        this.suggestions = [];
        this.showSuggestions = false;
      }
    });
  }

  ngOnDestroy(): void {
    if (this.searchSub) {
      this.searchSub.unsubscribe();
    }
  }

  onSearchInput(): void {
    if (!this.searchQuery || this.searchQuery.trim().length < 2) {
      this.suggestions = [];
      this.showSuggestions = false;
    } else {
      this.searchSubject.next(this.searchQuery);
    }
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.suggestions = [];
    this.showSuggestions = false;
    this.selectedSuggestionIndex = -1;
  }

  onKeyDown(event: KeyboardEvent): void {
    if (!this.showSuggestions || this.suggestions.length === 0) {
      if (event.key === 'Enter') {
        this.onSearch();
      }
      return;
    }

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.selectedSuggestionIndex = (this.selectedSuggestionIndex + 1) % this.suggestions.length;
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.selectedSuggestionIndex = this.selectedSuggestionIndex <= 0
        ? this.suggestions.length - 1
        : this.selectedSuggestionIndex - 1;
    } else if (event.key === 'Enter') {
      event.preventDefault();
      if (this.selectedSuggestionIndex >= 0 && this.selectedSuggestionIndex < this.suggestions.length) {
        this.selectSuggestion(this.suggestions[this.selectedSuggestionIndex]);
      } else {
        this.onSearch();
      }
    } else if (event.key === 'Escape') {
      this.showSuggestions = false;
    }
  }

  selectSuggestion(item: ProductSuggestion): void {
    this.showSuggestions = false;
    this.searchQuery = item.name;
    const target = item.slug || item.id;
    this.router.navigate(['/products', target]);
  }

  toggleMobileMenu(): void {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }

  closeMobileMenu(): void {
    this.isMobileMenuOpen = false;
    this.isHelpDropdownOpen = false;
    this.showSuggestions = false;
  }

  toggleHelpDropdown(event?: Event): void {
    if (event) {
      event.preventDefault();
      event.stopPropagation();
    }
    this.isHelpDropdownOpen = !this.isHelpDropdownOpen;
  }

  closeHelpDropdown(): void {
    this.isHelpDropdownOpen = false;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const clickedInside = this.elementRef.nativeElement.contains(event.target);
    if (!clickedInside) {
      this.isHelpDropdownOpen = false;
      this.showSuggestions = false;
    }
  }

  @HostListener('document:keydown.escape')
  onEscapePress(): void {
    this.isHelpDropdownOpen = false;
    this.isMobileMenuOpen = false;
    this.showLogoutConfirm = false;
    this.showSuggestions = false;
  }

  requestLogout(): void {
    this.showLogoutConfirm = true;
  }

  cancelLogout(): void {
    this.showLogoutConfirm = false;
  }

  confirmLogout(): void {
    this.showLogoutConfirm = false;
    this.authService.logout();
    this.closeMobileMenu();
    this.router.navigate(['/login']);
  }

  logout(): void {
    this.requestLogout();
  }

  onSearch(): void {
    if (this.searchQuery.trim()) {
      this.showSuggestions = false;
      this.closeMobileMenu();
      this.searchSubmitted.emit(this.searchQuery.trim());
      this.router.navigate(['/shop'], { queryParams: { query: this.searchQuery.trim() } });
    }
  }
}
