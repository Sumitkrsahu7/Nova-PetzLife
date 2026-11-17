import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
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

  logout(): void {
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
}
