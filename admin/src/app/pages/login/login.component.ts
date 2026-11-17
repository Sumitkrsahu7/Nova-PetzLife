import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AdminAuthService } from '../../core/services/admin-auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {
  identifier = '';
  password = '';
  showPassword = false;
  selectedRole: 'ROLE_SUPER_ADMIN' | 'ROLE_STAFF_ADMIN' = 'ROLE_SUPER_ADMIN';
  errorMessage = '';
  isLoading = false;

  constructor(
    private adminAuthService: AdminAuthService,
    private router: Router
  ) {
    if (this.adminAuthService.isAuthenticated()) {
      this.router.navigate(['/dashboard']);
    }
  }

  fillDemoCredentials(role: 'ROLE_SUPER_ADMIN' | 'ROLE_STAFF_ADMIN'): void {
    this.selectedRole = role;
    this.errorMessage = '';
    if (role === 'ROLE_SUPER_ADMIN') {
      this.identifier = 'test@gmail.com';
      this.password = 'Test@123';
    } else {
      this.identifier = 'staff@novapetzlife.com';
      this.password = 'Test@123';
    }
  }

  togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
  }

  onInputChange(): void {
    if (this.errorMessage) {
      this.errorMessage = '';
    }
  }

  onSubmit(): void {
    const rawId = this.identifier ? this.identifier.trim() : '';
    if (!rawId || !this.password) {
      this.errorMessage = 'Please provide both email/mobile number and password.';
      return;
    }

    // Universal Login Identifier Validation: email or 10-digit mobile number
    const emailRegex = /^[a-zA-Z0-9_+&*-]+(?:\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}$/;
    const phoneRegex = /^(\+91)?[6-9]\d{9}$/;

    if (!emailRegex.test(rawId) && !phoneRegex.test(rawId)) {
      this.errorMessage = 'Please enter a valid email address or 10-digit Indian mobile number.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.adminAuthService.login({ email: rawId, password: this.password }).subscribe({
      next: (res: any) => {
        this.isLoading = false;
        // Verify admin role authorization from payload or stored user
        const payload = (res && res.data) ? res.data : res;
        const roles: string[] = payload?.roles || this.adminAuthService.getCurrentUser()?.roles || [];
        const hasAdminRole = roles.includes('ROLE_SUPER_ADMIN') || roles.includes('ROLE_STAFF_ADMIN');
        if (!hasAdminRole) {
          this.adminAuthService.logout();
          this.errorMessage = 'Access denied. You do not have permission to access the admin portal.';
          return;
        }
        this.router.navigate(['/dashboard']);
      },
      error: (err: any) => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || err.message || 'Invalid administrator credentials or unauthorized account.';
      }
    });
  }
}

