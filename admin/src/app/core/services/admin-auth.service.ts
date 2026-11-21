import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, map, tap, catchError, of, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AdminAuthResponse {
  token: string;
  tokenType: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
  permissions?: string[];
}

export interface AdminUser {
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
  permissions?: string[];
}

@Injectable({
  providedIn: 'root'
})
export class AdminAuthService implements OnDestroy {
  private readonly TOKEN_KEY = 'nova_admin_token';
  private readonly USER_KEY = 'nova_admin_user';

  private currentUserSubject = new BehaviorSubject<AdminUser | null>(this.getStoredUser());
  public currentUser$ = this.currentUserSubject.asObservable();
  private authChannel: BroadcastChannel | null = null;

  constructor(private http: HttpClient) {
    if (typeof BroadcastChannel !== 'undefined') {
      try {
        this.authChannel = new BroadcastChannel('nova_admin_auth_sync');
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
      map(res => {
        // Normalize backend ApiResponse { success, data, message } or direct object
        return (res && res.data) ? res.data : res;
      }),
      tap((authData: any) => {
        if (authData?.token) {
          const roles: string[] = authData.roles || [];
          const isAdmin = roles.includes('ROLE_SUPER_ADMIN') || roles.includes('ROLE_STAFF_ADMIN');
          if (!isAdmin) {
            throw new Error('Access denied. Administrator privileges required.');
          }
          this.handleAuthSuccess(authData);
        }
      })
    );
  }

  refreshToken(): Observable<AdminAuthResponse> {
    return this.http.post<any>(`${environment.apiUrl}/auth/refresh`, {}, {
      withCredentials: true,
      headers: { 'X-Requested-With': 'XMLHttpRequest' }
    }).pipe(
      map(res => (res && res.data) ? res.data : res),
      tap((authData: AdminAuthResponse) => {
        if (authData?.token) {
          this.handleAuthSuccess(authData);
        }
      })
    );
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
      const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
      if (!payload.exp) return false;
      return Date.now() >= payload.exp * 1000;
    } catch {
      return true;
    }
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    return !!token && !this.isTokenExpired(token);
  }

  getCurrentUser(): AdminUser | null {
    return this.currentUserSubject.value;
  }

  hasRole(role: string): boolean {
    const user = this.getCurrentUser();
    return !!user && user.roles.includes(role);
  }

  hasPermission(permission: string): boolean {
    const user = this.getCurrentUser();
    if (!user) return false;
    if (user.roles?.includes('ROLE_SUPER_ADMIN')) return true;
    return !!user.permissions?.includes(permission);
  }

  private handleAuthSuccess(auth: AdminAuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, auth.token);
    const user: AdminUser = {
      email: auth.email,
      firstName: auth.firstName,
      lastName: auth.lastName,
      roles: auth.roles,
      permissions: auth.permissions || []
    };
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
    this.currentUserSubject.next(user);
  }

  private getStoredUser(): AdminUser | null {
    const raw = localStorage.getItem(this.USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }
}
