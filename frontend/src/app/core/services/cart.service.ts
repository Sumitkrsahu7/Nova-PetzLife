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
  private localCartStorageKey = 'nova_petzlife_local_cart_data';

  constructor(private http: HttpClient) {
    this.initSessionToken();
    const cached = this.loadLocalCartFromStorage();
    if (cached) {
      this.cartSubject.next(cached);
    }
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

  private loadLocalCartFromStorage(): Cart | null {
    try {
      const data = localStorage.getItem(this.localCartStorageKey);
      if (data) {
        return JSON.parse(data);
      }
    } catch {
      // Ignore JSON parse errors
    }
    return null;
  }

  private saveLocalCart(cart: Cart | null): void {
    try {
      if (cart) {
        localStorage.setItem(this.localCartStorageKey, JSON.stringify(cart));
      } else {
        localStorage.removeItem(this.localCartStorageKey);
      }
    } catch {
      // Ignore storage errors
    }
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
            this.saveLocalCart(res.data);
            this.cartSubject.next(res.data);
          }
        }),
        catchError(() => {
          const cached = this.loadLocalCartFromStorage();
          if (cached) {
            this.cartSubject.next(cached);
          }
          return of({ success: true, data: this.cartSubject.value });
        })
      );
  }

  private applyLocalAddItem(productId: number, variantId?: number, quantity: number = 1, details?: Partial<CartItem>): Cart {
    let current = this.cartSubject.value || this.loadLocalCartFromStorage();
    if (!current) {
      current = {
        id: Date.now(),
        sessionToken: this.getSessionToken(),
        items: [],
        totalItems: 0,
        subtotal: 0,
        currency: 'INR'
      };
    }

    const items = [...current.items];
    const existingIndex = items.findIndex(i =>
      i.productId === productId && (variantId ? i.variantId === variantId : !i.variantId)
    );

    const unitPrice = details?.unitPrice ?? (existingIndex >= 0 ? items[existingIndex].unitPrice : 0);

    if (existingIndex >= 0) {
      const existing = items[existingIndex];
      const newQty = existing.quantity + quantity;
      items[existingIndex] = {
        ...existing,
        quantity: newQty,
        totalPrice: newQty * existing.unitPrice
      };
    } else {
      const newItem: CartItem = {
        id: Date.now() + Math.floor(Math.random() * 1000),
        productId,
        variantId,
        productName: details?.productName || `Product #${productId}`,
        productSlug: details?.productSlug || '',
        brandName: details?.brandName || 'Nova Petzlife',
        variantName: details?.variantName,
        primaryImageUrl: details?.primaryImageUrl || 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?w=150&auto=format&fit=crop&q=80',
        quantity,
        unitPrice,
        totalPrice: unitPrice * quantity,
        availableStock: details?.availableStock || 20,
        returnAllowed: details?.returnAllowed ?? true,
        returnWindowDays: details?.returnWindowDays || 7
      };
      items.push(newItem);
    }

    const totalItems = items.reduce((sum, item) => sum + item.quantity, 0);
    const subtotal = items.reduce((sum, item) => sum + item.totalPrice, 0);

    const updatedCart: Cart = {
      ...current,
      items,
      totalItems,
      subtotal
    };

    this.saveLocalCart(updatedCart);
    this.cartSubject.next(updatedCart);
    return updatedCart;
  }

  public addItem(productId: number, variantId?: number, quantity: number = 1, details?: Partial<CartItem>): Observable<any> {
    // Immediately persist and update local cart state so item is immediately visible
    const localCart = this.applyLocalAddItem(productId, variantId, quantity, details);

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
            this.saveLocalCart(res.data);
            this.cartSubject.next(res.data);
          }
        }),
        catchError(() => {
          return of({ success: true, data: localCart });
        })
      );
  }

  public updateQuantity(itemId: number, quantity: number): Observable<any> {
    const current = this.cartSubject.value;
    if (current) {
      let items = [...current.items];
      if (quantity <= 0) {
        items = items.filter(i => i.id !== itemId);
      } else {
        const idx = items.findIndex(i => i.id === itemId);
        if (idx >= 0) {
          items[idx] = {
            ...items[idx],
            quantity,
            totalPrice: items[idx].unitPrice * quantity
          };
        }
      }
      const totalItems = items.reduce((sum, item) => sum + item.quantity, 0);
      const subtotal = items.reduce((sum, item) => sum + item.totalPrice, 0);
      const updated: Cart = { ...current, items, totalItems, subtotal };
      this.saveLocalCart(updated);
      this.cartSubject.next(updated);
    }

    return this.http.put<{ success: boolean; data: Cart }>(`${this.apiUrl}/items/${itemId}`, { quantity }, { headers: this.getHeaders() })
      .pipe(
        tap(res => {
          if (res?.success && res.data) {
            this.saveLocalCart(res.data);
            this.cartSubject.next(res.data);
          }
        }),
        catchError(() => of({ success: true, data: this.cartSubject.value }))
      );
  }

  public removeItem(itemId: number): Observable<any> {
    const current = this.cartSubject.value;
    if (current) {
      const items = current.items.filter(i => i.id !== itemId);
      const totalItems = items.reduce((sum, item) => sum + item.quantity, 0);
      const subtotal = items.reduce((sum, item) => sum + item.totalPrice, 0);
      const updated: Cart = { ...current, items, totalItems, subtotal };
      this.saveLocalCart(updated);
      this.cartSubject.next(updated);
    }

    return this.http.delete<{ success: boolean; data: Cart }>(`${this.apiUrl}/items/${itemId}`, { headers: this.getHeaders() })
      .pipe(
        tap(res => {
          if (res?.success && res.data) {
            this.saveLocalCart(res.data);
            this.cartSubject.next(res.data);
          }
        }),
        catchError(() => of({ success: true, data: this.cartSubject.value }))
      );
  }

  public clearCart(): Observable<any> {
    const current = this.cartSubject.value;
    const cleared: Cart = current ? { ...current, items: [], totalItems: 0, subtotal: 0 } : {
      id: Date.now(),
      sessionToken: this.getSessionToken(),
      items: [],
      totalItems: 0,
      subtotal: 0,
      currency: 'INR'
    };
    this.saveLocalCart(cleared);
    this.cartSubject.next(cleared);

    return this.http.delete<{ success: boolean }>(`${this.apiUrl}/clear`, { headers: this.getHeaders() })
      .pipe(
        catchError(() => of({ success: true }))
      );
  }

  public mergeCartOnLogin(): Observable<any> {
    const token = this.getSessionToken();
    return this.http.post<{ success: boolean; data: Cart }>(`${this.apiUrl}/merge`, { sessionToken: token }, { headers: this.getHeaders() })
      .pipe(
        tap(res => {
          if (res?.success && res.data) {
            this.saveLocalCart(res.data);
            this.cartSubject.next(res.data);
          }
        }),
        catchError(() => this.loadCart())
      );
  }

  public getCurrentCart(): Cart | null {
    return this.cartSubject.value;
  }
}
