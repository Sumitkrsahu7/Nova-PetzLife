import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
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
  @Input() isBackendOnline: boolean = false;
  @Output() searchSubmitted = new EventEmitter<string>();

  searchQuery: string = '';
  isMobileMenuOpen: boolean = false;

  constructor(
    private cartService: CartService,
    public authService: AuthService,
    private router: Router
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

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  onSearch(): void {
    if (this.searchQuery.trim()) {
      this.searchSubmitted.emit(this.searchQuery.trim());
    }
  }
}
