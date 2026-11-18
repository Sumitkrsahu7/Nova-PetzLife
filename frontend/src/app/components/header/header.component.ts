import { Component, Input, Output, EventEmitter, OnInit, HostListener, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { CartService } from '../../core/services/cart.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent implements OnInit {
  @Input() cartCount: number = 0;
  @Output() searchSubmitted = new EventEmitter<string>();

  searchQuery: string = '';
  isMobileMenuOpen: boolean = false;
  isHelpDropdownOpen: boolean = false;
  showLogoutConfirm: boolean = false;

  constructor(
    private cartService: CartService,
    public authService: AuthService,
    private router: Router,
    private elementRef: ElementRef
  ) { }

  ngOnInit(): void {
    this.cartService.cart$.subscribe(cart => {
      if (cart) {
        this.cartCount = cart.totalItems;
      }
    });
  }

  toggleMobileMenu(): void {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }

  closeMobileMenu(): void {
    this.isMobileMenuOpen = false;
    this.isHelpDropdownOpen = false;
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
    }
  }

  @HostListener('document:keydown.escape')
  onEscapePress(): void {
    this.isHelpDropdownOpen = false;
    this.isMobileMenuOpen = false;
    this.showLogoutConfirm = false;
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
      this.closeMobileMenu();
      this.searchSubmitted.emit(this.searchQuery.trim());
      this.router.navigate(['/shop'], { queryParams: { query: this.searchQuery.trim() } });
    }
  }
}
