import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CartService } from '../../core/services/cart.service';
import { BackButtonComponent } from '../../components/back-button/back-button.component';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, BackButtonComponent],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {
  mode: 'login' | 'register' = 'login';

  // Login fields
  identifier = '';
  password = '';
  showPassword = false;

  // Register fields
  regFirstName = '';
  regLastName = '';
  regEmail = '';
  regPhone = '';
  regPassword = '';
  regConfirmPassword = '';
  showRegPassword = false;
  showRegConfirm = false;

  isLoading = false;
  isGoogleLoading = false;
  googleButtonRendered = false;
  errorMessage = '';
  successMessage = '';
  returnUrl = '/';

  constructor(
    private authService: AuthService,
    private cartService: CartService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.returnUrl = params['returnUrl'] || '/';
      if (params['mode'] === 'register' || params['tab'] === 'register') {
        this.mode = 'register';
      } else {
        this.mode = 'login';
      }
    });

    if (this.authService.isAuthenticated()) {
      this.router.navigateByUrl(this.returnUrl);
      return;
    }

    setTimeout(() => this.initGoogleIdentity(), 100);
  }

  private initGoogleIdentity(): void {
    const clientId = environment.googleClientId;
    if (!clientId) {
      this.googleButtonRendered = false;
      return;
    }

    if (typeof (window as any).google === 'undefined') {
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.defer = true;
      script.onload = () => {
        this.renderGoogleButton(clientId);
      };
      document.head.appendChild(script);
    } else {
      this.renderGoogleButton(clientId);
    }
  }

  private renderGoogleButton(clientId: string): void {
    try {
      const google = (window as any).google;
      if (!google?.accounts?.id) return;

      google.accounts.id.initialize({
        client_id: clientId,
        callback: (response: any) => {
          if (response?.credential) {
            this.handleGoogleCredential(response.credential);
          }
        }
      });

      const loginContainer = document.getElementById('google-btn-login');
      if (loginContainer) {
        google.accounts.id.renderButton(loginContainer, {
          theme: 'outline',
          size: 'large',
          width: 320,
          text: 'continue_with'
        });
        this.googleButtonRendered = true;
      }

      const regContainer = document.getElementById('google-btn-register');
      if (regContainer) {
        google.accounts.id.renderButton(regContainer, {
          theme: 'outline',
          size: 'large',
          width: 320,
          text: 'continue_with'
        });
        this.googleButtonRendered = true;
      }
    } catch (e) {
      console.warn('Google Identity button rendering skipped:', e);
    }
  }

  handleGoogleClick(): void {
    if (!environment.googleClientId) {
      this.errorMessage = 'Google Sign-In is ready! Please configure GOOGLE_CLIENT_ID in your environment to complete the connection.';
      return;
    }
    const google = (window as any).google;
    if (google?.accounts?.id) {
      google.accounts.id.prompt();
    }
  }

  handleGoogleCredential(credential: string): void {
    if (this.isGoogleLoading) return;
    this.isGoogleLoading = true;
    this.errorMessage = '';

    this.authService.loginWithGoogle(credential).subscribe({
      next: () => {
        this.isGoogleLoading = false;
        this.cartService.mergeCartOnLogin().subscribe({ next: () => {}, error: () => {} });
        this.router.navigateByUrl(this.returnUrl);
      },
      error: err => {
        this.isGoogleLoading = false;
        this.errorMessage = err.error?.message || 'Google authentication failed. Please try again.';
      }
    });
  }

  setMode(newMode: 'login' | 'register'): void {
    this.mode = newMode;
    this.errorMessage = '';
    this.successMessage = '';
  }

  togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
  }

  toggleRegPasswordVisibility(): void {
    this.showRegPassword = !this.showRegPassword;
  }

  toggleRegConfirmVisibility(): void {
    this.showRegConfirm = !this.showRegConfirm;
  }

  onInputChange(): void {
    if (this.errorMessage) {
      this.errorMessage = '';
    }
  }

  // Password validation rules for registration
  get hasMinLength(): boolean {
    return this.regPassword.length >= 8;
  }

  get hasUpperCase(): boolean {
    return /[A-Z]/.test(this.regPassword);
  }

  get hasLowerCase(): boolean {
    return /[a-z]/.test(this.regPassword);
  }

  get hasNumber(): boolean {
    return /\d/.test(this.regPassword);
  }

  get hasSpecialChar(): boolean {
    return /[^a-zA-Z\d\s]/.test(this.regPassword);
  }

  get isRegPasswordValid(): boolean {
    return this.hasMinLength && this.hasUpperCase && this.hasLowerCase && this.hasNumber && this.hasSpecialChar;
  }

  get regPasswordsMatch(): boolean {
    return this.regConfirmPassword.length > 0 && this.regPassword === this.regConfirmPassword;
  }

  get regPasswordStrengthLabel(): string {
    if (!this.regPassword) return '';
    let score = 0;
    if (this.hasMinLength) score++;
    if (this.hasUpperCase) score++;
    if (this.hasLowerCase) score++;
    if (this.hasNumber) score++;
    if (this.hasSpecialChar) score++;
    if (score <= 2) return 'Weak';
    if (score <= 4) return 'Medium';
    return 'Strong';
  }

  onLoginSubmit(): void {
    const rawId = this.identifier ? this.identifier.trim() : '';
    if (!rawId || !this.password) {
      this.errorMessage = 'Please enter both email/mobile number and password.';
      return;
    }

    const emailRegex = /^[a-zA-Z0-9_+&*-]+(?:\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}$/;
    const phoneRegex = /^(\+91)?[6-9]\d{9}$/;

    if (!emailRegex.test(rawId) && !phoneRegex.test(rawId)) {
      this.errorMessage = 'Please enter a valid email address or 10-digit Indian mobile number.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.login({ email: rawId, password: this.password }).subscribe({
      next: () => {
        this.cartService.mergeCartOnLogin().subscribe({
          next: () => {
            this.isLoading = false;
            this.router.navigateByUrl(this.returnUrl);
          },
          error: () => {
            this.isLoading = false;
            this.router.navigateByUrl(this.returnUrl);
          }
        });
      },
      error: err => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || err.message || 'Invalid email/mobile number or password. Please try again.';
      }
    });
  }

  onRegisterSubmit(): void {
    if (!this.regFirstName.trim()) {
      this.errorMessage = 'Please enter your first name.';
      return;
    }

    const emailRegex = /^[a-zA-Z0-9_+&*-]+(?:\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}$/;
    if (!this.regEmail.trim() || !emailRegex.test(this.regEmail.trim())) {
      this.errorMessage = 'Please provide a valid email address.';
      return;
    }

    const rawDigits = (this.regPhone || '').replace(/\D/g, '');
    const cleanPhone = rawDigits.length >= 10 ? rawDigits.slice(-10) : rawDigits;
    const phoneRegex = /^[6-9]\d{9}$/;
    if (!cleanPhone || !phoneRegex.test(cleanPhone)) {
      this.errorMessage = 'Please enter a valid 10-digit Indian mobile number.';
      return;
    }

    if (!this.isRegPasswordValid) {
      this.errorMessage = 'Password must meet all 5 security requirements.';
      return;
    }

    if (this.regPassword !== this.regConfirmPassword) {
      this.errorMessage = 'Passwords do not match.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    const payload = {
      email: this.regEmail.trim().toLowerCase(),
      password: this.regPassword,
      firstName: this.regFirstName.trim(),
      lastName: this.regLastName ? this.regLastName.trim() : undefined,
      phone: cleanPhone
    };

    this.authService.register(payload).subscribe({
      next: () => {
        this.cartService.mergeCartOnLogin().subscribe({
          next: () => {
            this.isLoading = false;
            this.router.navigateByUrl(this.returnUrl);
          },
          error: () => {
            this.isLoading = false;
            this.router.navigateByUrl(this.returnUrl);
          }
        });
      },
      error: err => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || err.message || 'Registration failed. Please verify your details.';
      }
    });
  }
}
