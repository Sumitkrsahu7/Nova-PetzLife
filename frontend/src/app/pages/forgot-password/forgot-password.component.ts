import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './forgot-password.component.html',
  styleUrls: ['./forgot-password.component.scss']
})
export class ForgotPasswordComponent implements OnInit {
  email = '';
  isLoading = false;
  errorMessage = '';
  isSubmitted = false;

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/']);
    }
  }

  onSubmit(): void {
    const rawEmail = (this.email || '').trim().toLowerCase();
    const emailRegex = /^[a-zA-Z0-9_+&*-]+(?:\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}$/;

    if (!rawEmail || !emailRegex.test(rawEmail)) {
      this.errorMessage = 'Please enter a valid email address.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.forgotPassword(rawEmail).subscribe({
      next: () => {
        this.isLoading = false;
        this.isSubmitted = true;
      },
      error: () => {
        // Always display generic success feedback to prevent user enumeration
        this.isLoading = false;
        this.isSubmitted = true;
      }
    });
  }

  resetForm(): void {
    this.isSubmitted = false;
    this.email = '';
    this.errorMessage = '';
  }
}
