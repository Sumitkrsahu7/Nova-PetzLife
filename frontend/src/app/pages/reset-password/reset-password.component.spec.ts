import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResetPasswordComponent } from './reset-password.component';
import { AuthService } from '../../core/services/auth.service';
import { RouterTestingModule } from '@angular/router/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { FormsModule } from '@angular/forms';

describe('ResetPasswordComponent', () => {
  let component: ResetPasswordComponent;
  let fixture: ComponentFixture<ResetPasswordComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['validateResetToken', 'resetPassword']);
    authServiceSpy.validateResetToken.and.returnValue(of({ success: true, data: { valid: true } }));
    authServiceSpy.resetPassword.and.returnValue(of({ success: true, message: 'Password reset' }));

    await TestBed.configureTestingModule({
      imports: [ResetPasswordComponent, RouterTestingModule, FormsModule],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: of({ token: 'test_valid_token_123' })
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ResetPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the reset password component and extract token from queryParams', () => {
    expect(component).toBeTruthy();
    expect(component.token).toBe('test_valid_token_123');
    expect(authServiceSpy.validateResetToken).toHaveBeenCalledWith('test_valid_token_123');
    expect(component.isTokenValid).toBeTrue();
  });

  it('should validate password complexity requirements correctly', () => {
    component.newPassword = 'weak';
    expect(component.hasMinLength).toBeFalse();
    expect(component.isPasswordValid).toBeFalse();

    component.newPassword = 'StrongPassword123!';
    expect(component.hasMinLength).toBeTrue();
    expect(component.hasUpperCase).toBeTrue();
    expect(component.hasLowerCase).toBeTrue();
    expect(component.hasNumber).toBeTrue();
    expect(component.hasSpecialChar).toBeTrue();
    expect(component.isPasswordValid).toBeTrue();
  });

  it('should detect password confirmation mismatch', () => {
    component.newPassword = 'StrongPassword123!';
    component.confirmPassword = 'DifferentPassword123!';
    expect(component.passwordsMatch).toBeFalse();

    component.onSubmit();
    expect(component.errorMessage).toContain('do not match');
    expect(authServiceSpy.resetPassword).not.toHaveBeenCalled();
  });

  it('should submit valid new password and show success confirmation', () => {
    component.newPassword = 'StrongPassword123!';
    component.confirmPassword = 'StrongPassword123!';
    component.onSubmit();

    expect(authServiceSpy.resetPassword).toHaveBeenCalledWith({
      token: 'test_valid_token_123',
      newPassword: 'StrongPassword123!'
    });
    expect(component.isResetSuccess).toBeTrue();
    expect(component.errorMessage).toBe('');
  });

  it('should handle invalid or expired token during validation', () => {
    authServiceSpy.validateResetToken.and.returnValue(throwError(() => new Error('Expired link')));
    component.validateToken();

    expect(component.isTokenValid).toBeFalse();
    expect(component.errorMessage).toContain('invalid or has expired');
  });
});
