import { Injectable, OnDestroy } from '@angular/core';
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
export class AuthService implements OnDestroy {
  private readonly TOKEN_KEY = 'nova_user_token';
  private readonly USER_KEY = 'nova_user_profile';

  private currentUserSubject = new BehaviorSubject<UserProfile | null>(this.getStoredUser());
  public currentUser$ = this.currentUserSubject.asObservable();
  private authChannel: BroadcastChannel | null = null;

  constructor(private http: HttpClient) {
    if (typeof BroadcastChannel !== 'undefined') {
      try {
        this.authChannel = new BroadcastChannel('nova_cust_auth_sync');
        this.authChannel.onmessage = (event: MessageEvent) => {
          if (event.data?.type === 'LOGOUT') {
            this.handleRemoteLogout();
          }
        };
      } catch (_) {
        this.authChannel = null;
      }
    }
  }

  ngOnDestroy(): void {
    try {
      this.authChannel?.close();
    } catch (_) { }
  }

  private handleRemoteLogout(): void {
    try {
      localStorage.removeItem(this.TOKEN_KEY);
      localStorage.removeItem(this.USER_KEY);
    } catch (_) { }
    this.currentUserSubject.next(null);
  }

  login(credentials: { email: string; password: string }): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/auth/login`, credentials, { withCredentials: true }).pipe(
      tap(res => {
        const authData = res?.data || res;
        if (authData?.token) {
          this.handleAuthSuccess(authData);
        }
      })
    );
  }

  register(payload: { email: string; password: string; firstName: string; lastName?: string; phone?: string }): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/auth/register`, payload, { withCredentials: true }).pipe(
      tap(res => {
        const authData = res?.data || res;
        if (authData?.token) {
          this.handleAuthSuccess(authData);
        }
      })
    );
  }

  refreshToken(): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/auth/refresh`, {}, {
      withCredentials: true,
      headers: { 'X-Requested-With': 'XMLHttpRequest' }
    }).pipe(
      tap(res => {
        const authData = res?.data || res;
        if (authData?.token) {
          this.handleAuthSuccess(authData);
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

  forgotPassword(email: string): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/auth/forgot-password`, { email: email.trim().toLowerCase() });
  }

  validateResetToken(token: string): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/auth/reset-password/validate`, { token: token.trim() });
  }

  resetPassword(payload: { token: string; newPassword: string }): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/auth/reset-password`, {
      token: payload.token.trim(),
      newPassword: payload.newPassword
    });
  }

  logout(callApi: boolean = true, broadcast: boolean = true): void {
    if (callApi) {
      this.http.post(`${environment.apiUrl}/auth/logout`, {}, {
        withCredentials: true,
        headers: { 'X-Requested-With': 'XMLHttpRequest' }
      })
        .pipe(catchError(() => of(null)))
        .subscribe();
    }
    try {
      localStorage.removeItem(this.TOKEN_KEY);
      localStorage.removeItem(this.USER_KEY);
    } catch (_) { }
    this.currentUserSubject.next(null);

    if (broadcast && this.authChannel) {
      try {
        this.authChannel.postMessage({ type: 'LOGOUT' });
      } catch (_) { }
    }
  }

  setToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  isTokenExpired(token: string | null): boolean {
    if (!token) return true;
    try {
      const parts = token.split('.');
      if (parts.length < 2) return true;
      const payloadBase64 = parts[1];
      const payloadJson = atob(payloadBase64.replace(/-/g, '+').replace(/_/g, '/'));
      const payload = JSON.parse(payloadJson);
      if (payload.exp) {
        return payload.exp * 1000 <= Date.now();
      }
      return false;
    } catch {
      return true;
    }
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    return !!token && !this.isTokenExpired(token);
  }

  getCurrentUser(): UserProfile | null {
    return this.currentUserSubject.value;
  }

  updateCurrentUser(user: UserProfile, token?: string): void {
    if (token) {
      localStorage.setItem(this.TOKEN_KEY, token);
    }
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
    this.currentUserSubject.next(user);
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
}
