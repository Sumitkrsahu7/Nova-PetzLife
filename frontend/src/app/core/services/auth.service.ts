import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap, catchError, of } from 'rxjs';
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

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly TOKEN_KEY = 'nova_user_token';
  private readonly USER_KEY = 'nova_user_profile';

  private currentUserSubject = new BehaviorSubject<UserProfile | null>(this.getStoredUser());
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {}

  login(credentials: { email: string; password: string }): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/auth/login`, credentials).pipe(
      tap(res => {
        if (res?.success && res.data?.token) {
          this.handleAuthSuccess(res.data);
        }
      })
    );
  }

  register(payload: { email: string; password: string; firstName: string; lastName?: string; phone?: string }): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/auth/register`, payload).pipe(
      tap(res => {
        if (res?.success && res.data?.token) {
          this.handleAuthSuccess(res.data);
        }
      })
    );
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
    const raw = localStorage.getItem(this.USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  private isTokenExpired(token: string): boolean {
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
