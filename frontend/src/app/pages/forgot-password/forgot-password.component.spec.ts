import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ForgotPasswordComponent } from './forgot-password.component';
import { AuthService } from '../../core/services/auth.service';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { FormsModule } from '@angular/forms';

describe('ForgotPasswordComponent', () => {
  let component: ForgotPasswordComponent;
  let fixture: ComponentFixture<ForgotPasswordComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'forgotPassword']);
    authServiceSpy.isAuthenticated.and.returnValue(false);
    authServiceSpy.forgotPassword.and.returnValue(of({ success: true, message: 'Reset email sent' }));

    await TestBed.configureTestingModule({
      imports: [ForgotPasswordComponent, RouterTestingModule, FormsModule],
      providers: [
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ForgotPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the forgot password component', () => {
    expect(component).toBeTruthy();
  });

  it('should validate email format and reject invalid emails', () => {
    component.email = 'not-an-email';
    component.onSubmit();
    expect(component.errorMessage).toContain('valid email address');
    expect(authServiceSpy.forgotPassword).not.toHaveBeenCalled();
  });

  it('should call authService.forgotPassword and show generic confirmation on success', () => {
    component.email = 'customer@example.com';
    component.onSubmit();
    expect(authServiceSpy.forgotPassword).toHaveBeenCalledWith('customer@example.com');
    expect(component.isSubmitted).toBeTrue();
    expect(component.errorMessage).toBe('');
  });

  it('should display generic confirmation even if backend returns error to avoid account enumeration', () => {
    authServiceSpy.forgotPassword.and.returnValue(throwError(() => new Error('Account not found')));
    component.email = 'nonexistent@example.com';
    component.onSubmit();
    expect(authServiceSpy.forgotPassword).toHaveBeenCalledWith('nonexistent@example.com');
    expect(component.isSubmitted).toBeTrue();
  });

  it('should reset form state when resetForm is called', () => {
    component.isSubmitted = true;
    component.email = 'test@example.com';
    component.resetForm();
    expect(component.isSubmitted).toBeFalse();
    expect(component.email).toBe('');
    expect(component.errorMessage).toBe('');
  });
});
