import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './reset-password.component.html',
  styleUrls: ['./reset-password.component.scss']
})
export class ResetPasswordComponent implements OnInit {
  token = '';
  newPassword = '';
  confirmPassword = '';
  showNewPassword = false;
  showConfirmPassword = false;

  isValidatingToken = true;
  isTokenValid = false;
  isLoading = false;
  errorMessage = '';
  isResetSuccess = false;

  constructor(
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.token = params['token'] || '';
      if (!this.token.trim()) {
        this.isValidatingToken = false;
        this.isTokenValid = false;
        this.errorMessage = 'No reset token was provided in the link. Please request a new password reset link.';
      } else {
        this.validateToken();
      }
    });
  }

  validateToken(): void {
    this.isValidatingToken = true;
    this.errorMessage = '';

    this.authService.validateResetToken(this.token).subscribe({
      next: (res) => {
        this.isValidatingToken = false;
        this.isTokenValid = res?.data?.valid !== false;
      },
      error: () => {
        this.isValidatingToken = false;
        this.isTokenValid = false;
        this.errorMessage = 'This password reset link is invalid or has expired. Please request a new one.';
      }
    });
  }

  toggleNewPassword(): void {
    this.showNewPassword = !this.showNewPassword;
  }

  toggleConfirmPassword(): void {
    this.showConfirmPassword = !this.showConfirmPassword;
  }

  // Password requirements
  get hasMinLength(): boolean {
    return this.newPassword.length >= 8;
  }

  get hasUpperCase(): boolean {
    return /[A-Z]/.test(this.newPassword);
  }

  get hasLowerCase(): boolean {
    return /[a-z]/.test(this.newPassword);
  }

  get hasNumber(): boolean {
    return /\d/.test(this.newPassword);
  }

  get hasSpecialChar(): boolean {
    return /[^a-zA-Z\d\s]/.test(this.newPassword);
  }

  get isPasswordValid(): boolean {
    return this.hasMinLength && this.hasUpperCase && this.hasLowerCase && this.hasNumber && this.hasSpecialChar;
  }

  get passwordsMatch(): boolean {
    return this.confirmPassword.length > 0 && this.newPassword === this.confirmPassword;
  }

  onSubmit(): void {
    if (!this.isPasswordValid) {
      this.errorMessage = 'Please ensure your password satisfies all 5 security requirements.';
      return;
    }

    if (this.newPassword !== this.confirmPassword) {
      this.errorMessage = 'Passwords do not match.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.resetPassword({
      token: this.token,
      newPassword: this.newPassword
    }).subscribe({
      next: () => {
        this.isLoading = false;
        this.isResetSuccess = true;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || err.message || 'Failed to reset password. The link may have expired.';
      }
    });
  }
}
