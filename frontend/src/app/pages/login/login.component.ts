import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {
  identifier = '';
  password = '';
  showPassword = false;
  isLoading = false;
  errorMessage = '';
  returnUrl = '/';

  constructor(
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/';
    if (this.authService.isAuthenticated()) {
      this.router.navigate([this.returnUrl]);
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
      this.errorMessage = 'Please enter both email/mobile number and password.';
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

    this.authService.login({ email: rawId, password: this.password }).subscribe({
      next: res => {
        this.isLoading = false;
        this.router.navigateByUrl(this.returnUrl);
      },
      error: err => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Invalid email/mobile number or password. Please try again.';
      }
    });
  }
}

