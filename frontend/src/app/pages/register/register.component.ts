import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss']
})
export class RegisterComponent {
  firstName = '';
  lastName = '';
  email = '';
  phone = '';
  password = '';
  confirmPassword = '';
  showPassword = false;
  showConfirmPassword = false;
  isLoading = false;
  errorMessage = '';

  constructor(
    private authService: AuthService,
    private router: Router
  ) {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/']);
    }
  }

  togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
  }

  toggleConfirmPasswordVisibility(): void {
    this.showConfirmPassword = !this.showConfirmPassword;
  }

  onInputChange(): void {
    if (this.errorMessage) {
      this.errorMessage = '';
    }
  }

  // Password rules checklist
  get hasMinLength(): boolean {
    return this.password.length >= 8;
  }

  get hasUpperCase(): boolean {
    return /[A-Z]/.test(this.password);
  }

  get hasLowerCase(): boolean {
    return /[a-z]/.test(this.password);
  }

  get hasNumber(): boolean {
    return /\d/.test(this.password);
  }

  get hasSpecialChar(): boolean {
    return /[^a-zA-Z\d\s]/.test(this.password);
  }

  get isPasswordValid(): boolean {
    return this.hasMinLength && this.hasUpperCase && this.hasLowerCase && this.hasNumber && this.hasSpecialChar;
  }

  get passwordsMatch(): boolean {
    return this.confirmPassword.length > 0 && this.password === this.confirmPassword;
  }

  get passwordStrengthScore(): number {
    let score = 0;
    if (this.hasMinLength) score++;
    if (this.hasUpperCase) score++;
    if (this.hasLowerCase) score++;
    if (this.hasNumber) score++;
    if (this.hasSpecialChar) score++;
    return score;
  }

  get passwordStrengthLabel(): string {
    if (!this.password) return '';
    const score = this.passwordStrengthScore;
    if (score <= 2) return 'Weak';
    if (score <= 4) return 'Medium';
    return 'Strong';
  }

  get passwordStrengthClass(): string {
    if (!this.password) return '';
    const score = this.passwordStrengthScore;
    if (score <= 2) return 'strength-weak';
    if (score <= 4) return 'strength-medium';
    return 'strength-strong';
  }

  onSubmit(): void {
    const rawEmail = this.email ? this.email.trim() : '';
    const rawPhone = this.phone ? this.phone.trim() : '';
    const rawFirstName = this.firstName ? this.firstName.trim() : '';

    if (!rawFirstName || !rawEmail || !rawPhone || !this.password || !this.confirmPassword) {
      this.errorMessage = 'Please complete all required fields.';
      return;
    }

    // Email format validation
    const emailRegex = /^[a-zA-Z0-9_+&*-]+(?:\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}$/;
    if (!emailRegex.test(rawEmail)) {
      this.errorMessage = 'Please enter a valid email address.';
      return;
    }

    // Indian 10-digit mobile number validation
    const phoneRegex = /^(\+91)?[6-9]\d{9}$/;
    if (!phoneRegex.test(rawPhone)) {
      this.errorMessage = 'Please enter a valid 10-digit Indian mobile number (e.g. 9847012345).';
      return;
    }

    // Password strength policy validation
    if (!this.isPasswordValid) {
      this.errorMessage = 'Password does not meet the minimum security requirements (min 8 characters, 1 uppercase, 1 lowercase, 1 number, 1 special character).';
      return;
    }

    if (this.password !== this.confirmPassword) {
      this.errorMessage = 'Passwords do not match.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    const payload = {
      email: rawEmail,
      password: this.password,
      firstName: rawFirstName,
      lastName: this.lastName ? this.lastName.trim() : '',
      phone: rawPhone
    };

    this.authService.register(payload).subscribe({
      next: res => {
        this.isLoading = false;
        this.router.navigate(['/']);
      },
      error: err => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Registration failed. Email or phone number may already be in use.';
      }
    });
  }
}

