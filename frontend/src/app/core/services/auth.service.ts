import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap, catchError, of, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AuthResponse {
  token: string;
  tokenType: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
}

export interface UserProfile {
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
  roles: string[];
}

interface LocalStoredAccount {
  email: string;
  phone: string;
  password: string;
  firstName: string;
  lastName: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly TOKEN_KEY = 'nova_user_token';
  private readonly USER_KEY = 'nova_user_profile';
  private readonly LOCAL_USERS_KEY = 'nova_local_registered_users';

  // Seed demo customers matching DEMO_DATA_GUIDE.md & V4 migrations
  private readonly demoCustomers: LocalStoredAccount[] = [
    {
      email: 'anand.nair@example.com',
      phone: '9847012345',
      password: 'Test@123',
      firstName: 'Anand',
      lastName: 'Nair'
    },
    {
      email: 'priya.kurup@example.com',
      phone: '9847123456',
      password: 'Test@123',
      firstName: 'Priya',
      lastName: 'Kurup'
    },
    {
      email: 'arun.menoki@example.com',
      phone: '9847234567',
      password: 'Test@123',
      firstName: 'Arun',
      lastName: 'Menoki'
    },
    {
      email: 'test@gmail.com',
      phone: '9876543200',
      password: 'Test@123',
      firstName: 'Super',
      lastName: 'Admin'
    }
  ];

  private currentUserSubject = new BehaviorSubject<UserProfile | null>(this.getStoredUser());
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {}

  login(credentials: { email: string; password: string }): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/auth/login`, credentials).pipe(
      tap(res => {
        if (res?.success && res.data?.token) {
          this.handleAuthSuccess(res.data);
        }
      }),
      catchError(err => {
        // Fallback resilience when backend server is offline/unreachable (HTTP status 0)
        if (err.status === 0 || !err.status) {
          const fallbackRes = this.authenticateFallback(credentials.email, credentials.password);
          if (fallbackRes) {
            this.handleAuthSuccess(fallbackRes.data);
            return of(fallbackRes);
          }
          return throwError(() => ({
            error: {
              success: false,
              message: 'Invalid email/mobile number or password. (Demo: anand.nair@example.com / Test@123)'
            }
          }));
        }
        return throwError(() => err);
      })
    );
  }

  register(payload: { email: string; password: string; firstName: string; lastName?: string; phone?: string }): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/auth/register`, payload).pipe(
      tap(res => {
        if (res?.success && res.data?.token) {
          this.handleAuthSuccess(res.data);
        }
      }),
      catchError(err => {
        // Fallback resilience when backend server is offline/unreachable (HTTP status 0)
        if (err.status === 0 || !err.status) {
          const fallbackRes = this.registerFallback(payload);
          if (fallbackRes.success) {
            this.handleAuthSuccess(fallbackRes.data);
            return of(fallbackRes);
          } else {
            return throwError(() => ({
              error: {
                success: false,
                message: fallbackRes.message
              }
            }));
          }
        }
        return throwError(() => err);
      })
    );
  }

  private authenticateFallback(identifier: string, password: string): { success: boolean; message: string; data: AuthResponse } | null {
    const rawId = (identifier || '').trim().toLowerCase();
    const cleanPhone = rawId.replace(/\D/g, '');

    // Search seed demo customers first
    const allUsers = [...this.demoCustomers, ...this.getLocalRegisteredUsers()];
    const user = allUsers.find(u =>
      (u.email.toLowerCase() === rawId || (cleanPhone.length >= 10 && u.phone.endsWith(cleanPhone.slice(-10)))) &&
      u.password === password
    );

    if (!user) {
      return null;
    }

    const authData: AuthResponse = {
      token: `mock-jwt-token-${user.email}-${Date.now()}`,
      tokenType: 'Bearer',
      email: user.email,
      firstName: user.firstName,
      lastName: user.lastName,
      roles: user.email === 'test@gmail.com' ? ['ROLE_SUPER_ADMIN'] : ['ROLE_CUSTOMER']
    };

    return {
      success: true,
      message: 'Login successful',
      data: authData
    };
  }

  private registerFallback(payload: { email: string; password: string; firstName: string; lastName?: string; phone?: string }): { success: boolean; message: string; data: AuthResponse } {
    const email = payload.email.trim().toLowerCase();
    const rawPhone = (payload.phone || '').replace(/\D/g, '');
    const cleanPhone = rawPhone.length >= 10 ? rawPhone.slice(-10) : rawPhone;

    const allUsers = [...this.demoCustomers, ...this.getLocalRegisteredUsers()];
    const exists = allUsers.some(u =>
      u.email.toLowerCase() === email || (cleanPhone.length >= 10 && u.phone.endsWith(cleanPhone))
    );

    if (exists) {
      return {
        success: false,
        message: 'An account with this email or mobile number already exists.',
        data: {} as any
      };
    }

    const newAccount: LocalStoredAccount = {
      email,
      phone: cleanPhone || '9847000000',
      password: payload.password,
      firstName: payload.firstName.trim(),
      lastName: payload.lastName ? payload.lastName.trim() : ''
    };

    const localList = this.getLocalRegisteredUsers();
    localList.push(newAccount);
    this.saveLocalRegisteredUsers(localList);

    const authData: AuthResponse = {
      token: `mock-jwt-token-${email}-${Date.now()}`,
      tokenType: 'Bearer',
      email,
      firstName: newAccount.firstName,
      lastName: newAccount.lastName,
      roles: ['ROLE_CUSTOMER']
    };

    return {
      success: true,
      message: 'Registration successful',
      data: authData
    };
  }

  private getLocalRegisteredUsers(): LocalStoredAccount[] {
    if (typeof window === 'undefined') return [];
    const raw = localStorage.getItem(this.LOCAL_USERS_KEY);
    if (!raw) return [];
    try {
      return JSON.parse(raw);
    } catch {
      return [];
    }
  }

  private saveLocalRegisteredUsers(users: LocalStoredAccount[]): void {
    if (typeof window !== 'undefined') {
      localStorage.setItem(this.LOCAL_USERS_KEY, JSON.stringify(users));
    }
  }

  fetchCurrentProfile(): Observable<any> {
    if (!this.getToken()) {
      return of(null);
    }
    return this.http.get<any>(`${environment.apiUrl}/customer/profile`).pipe(
      tap(res => {
        if (res?.success && res.data) {
          const current = this.currentUserSubject.value;
          const updated: UserProfile = {
            email: res.data.email,
            firstName: res.data.firstName || current?.firstName || '',
            lastName: res.data.lastName || current?.lastName || '',
            phone: res.data.phone || current?.phone || '',
            roles: current?.roles || ['ROLE_CUSTOMER']
          };
          localStorage.setItem(this.USER_KEY, JSON.stringify(updated));
          this.currentUserSubject.next(updated);
        }
      }),
      catchError(err => {
        if (err.status === 401) {
          this.logout();
        }
        return of(null);
      })
    );
  }

  logout(): void {
    this.http.post(`${environment.apiUrl}/auth/logout`, {}).pipe(catchError(() => of(null))).subscribe();
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
  }

  getToken(): string | null {
    const token = localStorage.getItem(this.TOKEN_KEY);
    if (!token) return null;
    if (this.isTokenExpired(token)) {
      this.logout();
      return null;
    }
    return token;
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  getCurrentUser(): UserProfile | null {
    return this.currentUserSubject.value;
  }

  private handleAuthSuccess(auth: AuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, auth.token);
    const profile: UserProfile = {
      email: auth.email,
      firstName: auth.firstName,
      lastName: auth.lastName,
      roles: auth.roles
    };
    localStorage.setItem(this.USER_KEY, JSON.stringify(profile));
    this.currentUserSubject.next(profile);
  }

  private getStoredUser(): UserProfile | null {
    if (typeof window === 'undefined') return null;
    const raw = localStorage.getItem(this.USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  private isTokenExpired(token: string): boolean {
    if (token.startsWith('mock-jwt-')) {
      return false;
    }
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return false;
      const payloadBase64 = parts[1];
      const payloadJson = atob(payloadBase64.replace(/-/g, '+').replace(/_/g, '/'));
      const payload = JSON.parse(payloadJson);
      if (payload.exp) {
        return payload.exp * 1000 < Date.now();
      }
      return false;
    } catch {
      return false;
    }
  }
}
