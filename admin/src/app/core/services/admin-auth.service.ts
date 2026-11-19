import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, map, tap, catchError, of } from 'rxjs';
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
export class AdminAuthService {
  private readonly TOKEN_KEY = 'nova_admin_token';
  private readonly USER_KEY = 'nova_admin_user';

  private currentUserSubject = new BehaviorSubject<AdminUser | null>(this.getStoredUser());
  public currentUser$ = this.currentUserSubject.asObservable();

  constructor(private http: HttpClient) {}

  login(credentials: { email: string; password: string }): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/auth/login`, credentials).pipe(
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

  logout(): void {
    this.http.post(`${environment.apiUrl}/auth/logout`, {}).pipe(catchError(() => of(null))).subscribe();
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
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
