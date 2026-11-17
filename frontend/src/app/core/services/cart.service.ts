import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable, tap, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface CartItem {
  id: number;
  productId: number;
  productName: string;
  productSlug: string;
  brandName: string;
  variantId?: number;
  variantName?: string;
  primaryImageUrl?: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  availableStock: number;
  returnAllowed: boolean;
  returnWindowDays: number;
}

export interface Cart {
  id: number;
  sessionToken: string;
  items: CartItem[];
  totalItems: number;
  subtotal: number;
  currency: string;
}

@Injectable({
  providedIn: 'root'
})
export class CartService {
  private apiUrl = `${environment.apiUrl}/cart`;
  private cartSubject = new BehaviorSubject<Cart | null>(null);
  public cart$ = this.cartSubject.asObservable();

  private sessionTokenKey = 'nova_petzlife_session_token';

  constructor(private http: HttpClient) {
    this.initSessionToken();
    this.loadCart().subscribe();
  }

  public getSessionToken(): string {
    let token = localStorage.getItem(this.sessionTokenKey);
    if (!token) {
      token = 'npz_guest_' + Math.random().toString(36).substring(2, 15) + Date.now().toString(36);
      localStorage.setItem(this.sessionTokenKey, token);
    }
    return token;
  }

  private initSessionToken(): void {
    this.getSessionToken();
  }

  private getHeaders(): HttpHeaders {
    return new HttpHeaders({
      'X-Session-Token': this.getSessionToken()
    });
  }

  public loadCart(): Observable<any> {
    return this.http.get<{ success: boolean; data: Cart }>(this.apiUrl, { headers: this.getHeaders() })
      .pipe(
        tap(res => {
          if (res?.success && res.data) {
            this.cartSubject.next(res.data);
          }
        }),
        catchError(() => {
          return of(null);
        })
      );
  }

  public addItem(productId: number, variantId?: number, quantity: number = 1): Observable<any> {
    const body = {
      productId,
      variantId,
      quantity,
      sessionToken: this.getSessionToken()
    };

    return this.http.post<{ success: boolean; data: Cart }>(`${this.apiUrl}/items`, body, { headers: this.getHeaders() })
      .pipe(
        tap(res => {
          if (res?.success && res.data) {
            this.cartSubject.next(res.data);
          }
        })
      );
  }

  public updateQuantity(itemId: number, quantity: number): Observable<any> {
    return this.http.put<{ success: boolean; data: Cart }>(`${this.apiUrl}/items/${itemId}`, { quantity }, { headers: this.getHeaders() })
      .pipe(
        tap(res => {
          if (res?.success && res.data) {
            this.cartSubject.next(res.data);
          }
        })
      );
  }

  public removeItem(itemId: number): Observable<any> {
    return this.http.delete<{ success: boolean; data: Cart }>(`${this.apiUrl}/items/${itemId}`, { headers: this.getHeaders() })
      .pipe(
        tap(res => {
          if (res?.success && res.data) {
            this.cartSubject.next(res.data);
          }
        })
      );
  }

  public clearCart(): Observable<any> {
    return this.http.delete<{ success: boolean }>(`${this.apiUrl}/clear`, { headers: this.getHeaders() })
      .pipe(
        tap(() => {
          const current = this.cartSubject.value;
          if (current) {
            this.cartSubject.next({ ...current, items: [], totalItems: 0, subtotal: 0 });
          }
        })
      );
  }

  public getCurrentCart(): Cart | null {
    return this.cartSubject.value;
  }
}
