import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('AuthInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['getToken', 'refreshToken', 'logout', 'isTokenExpired', 'setToken']);
    authServiceSpy.isTokenExpired.and.returnValue(false);
    routerSpy = jasmine.createSpyObj('Router', ['navigate'], { url: '/customer/profile' });

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should attach Bearer token to standard customer requests when token exists', () => {
    authServiceSpy.getToken.and.returnValue('mock-customer-access-token');

    http.get('/api/v1/customer/profile').subscribe();

    const req = httpMock.expectOne('/api/v1/customer/profile');
    expect(req.request.headers.get('Authorization')).toBe('Bearer mock-customer-access-token');
    req.flush({ success: true });
  });

  it('should not attach Authorization header to /auth/refresh request', () => {
    authServiceSpy.getToken.and.returnValue('mock-customer-access-token');

    http.post('/api/v1/auth/refresh', {}).subscribe();

    const req = httpMock.expectOne('/api/v1/auth/refresh');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({ success: true });
  });

  it('should not attach Authorization header when token is null', () => {
    authServiceSpy.getToken.and.returnValue(null);

    http.get('/api/v1/products').subscribe();

    const req = httpMock.expectOne('/api/v1/products');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({ success: true });
  });

  it('should refresh token on 401 and retry request with new Bearer token', () => {
    authServiceSpy.getToken.and.returnValue('expired-token');
    authServiceSpy.refreshToken.and.returnValue(of({ success: true, data: { token: 'new-refreshed-token' } }));

    let responseData: any;
    http.get('/api/v1/customer/orders').subscribe(data => responseData = data);

    const firstReq = httpMock.expectOne('/api/v1/customer/orders');
    expect(firstReq.request.headers.get('Authorization')).toBe('Bearer expired-token');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.refreshToken).toHaveBeenCalled();

    const retryReq = httpMock.expectOne('/api/v1/customer/orders');
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer new-refreshed-token');
    expect(retryReq.request.headers.get('X-Refresh-Retry')).toBe('true');
    retryReq.flush({ success: true, orders: [] });

    expect(responseData).toEqual({ success: true, orders: [] });
  });

  it('should call authService.logout(false) on refresh failure to prevent duplicate logout API calls', () => {
    authServiceSpy.getToken.and.returnValue('expired-token');
    authServiceSpy.refreshToken.and.returnValue(throwError(() => ({ status: 401, message: 'Invalid refresh token' })));

    let hasError = false;
    http.get('/api/v1/customer/orders').subscribe({
      error: () => hasError = true
    });

    const firstReq = httpMock.expectOne('/api/v1/customer/orders');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.logout).toHaveBeenCalledWith(false);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/customer/profile' } });
    expect(hasError).toBeTrue();
  });

  it('should retry immediately without calling refreshToken if token in storage is already refreshed and valid', () => {
    // Initial token was old-token, but storage now returns fresh-token
    authServiceSpy.getToken.and.returnValues('old-token', 'fresh-token');

    let responseData: any;
    http.get('/api/v1/customer/orders').subscribe(data => responseData = data);

    const firstReq = httpMock.expectOne('/api/v1/customer/orders');
    expect(firstReq.request.headers.get('Authorization')).toBe('Bearer old-token');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    // Should NOT call refreshToken because fresh-token is already present in storage
    expect(authServiceSpy.refreshToken).not.toHaveBeenCalled();

    const retryReq = httpMock.expectOne('/api/v1/customer/orders');
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer fresh-token');
    expect(retryReq.request.headers.get('X-Refresh-Retry')).toBe('true');
    retryReq.flush({ success: true });

    expect(responseData).toEqual({ success: true });
  });

  it('should prevent retry loops when request already has X-Refresh-Retry', () => {
    authServiceSpy.getToken.and.returnValue('some-token');

    let hasError = false;
    http.get('/api/v1/customer/orders', {
      headers: { 'X-Refresh-Retry': 'true' }
    }).subscribe({
      error: () => hasError = true
    });

    const req = httpMock.expectOne('/api/v1/customer/orders');
    req.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.refreshToken).not.toHaveBeenCalled();
    expect(authServiceSpy.logout).toHaveBeenCalledWith(false);
    expect(hasError).toBeTrue();
  });

  it('should preserve original custom headers on stale-token retry', () => {
    authServiceSpy.getToken.and.returnValues('old-token', 'fresh-token');

    http.get('/api/v1/customer/orders', {
      headers: { 'X-Custom-Client': 'CustomerPortal', 'X-Correlation-Id': 'corr-123' }
    }).subscribe();

    const firstReq = httpMock.expectOne('/api/v1/customer/orders');
    expect(firstReq.request.headers.get('Authorization')).toBe('Bearer old-token');
    expect(firstReq.request.headers.get('X-Custom-Client')).toBe('CustomerPortal');
    expect(firstReq.request.headers.get('X-Correlation-Id')).toBe('corr-123');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.refreshToken).not.toHaveBeenCalled();

    const retryReq = httpMock.expectOne('/api/v1/customer/orders');
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer fresh-token');
    expect(retryReq.request.headers.get('X-Refresh-Retry')).toBe('true');
    expect(retryReq.request.headers.get('X-Custom-Client')).toBe('CustomerPortal');
    expect(retryReq.request.headers.get('X-Correlation-Id')).toBe('corr-123');
    retryReq.flush({ success: true });
  });

  it('should preserve original custom headers on local refresh retry', () => {
    authServiceSpy.getToken.and.returnValue('expired-token');
    authServiceSpy.refreshToken.and.returnValue(of({ success: true, data: { token: 'new-refreshed-token' } }));

    http.get('/api/v1/customer/orders', {
      headers: { 'X-Trace-Id': 'trace-999' }
    }).subscribe();

    const firstReq = httpMock.expectOne('/api/v1/customer/orders');
    expect(firstReq.request.headers.get('X-Trace-Id')).toBe('trace-999');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    const retryReq = httpMock.expectOne('/api/v1/customer/orders');
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer new-refreshed-token');
    expect(retryReq.request.headers.get('X-Refresh-Retry')).toBe('true');
    expect(retryReq.request.headers.get('X-Trace-Id')).toBe('trace-999');
    retryReq.flush({ success: true });
  });

  it('should not log out when retried request returns 403', () => {
    authServiceSpy.getToken.and.returnValue('expired-token');
    authServiceSpy.refreshToken.and.returnValue(of({ success: true, data: { token: 'new-refreshed-token' } }));

    let caughtError: any;
    http.get('/api/v1/customer/forbidden-resource').subscribe({
      error: err => caughtError = err
    });

    const firstReq = httpMock.expectOne('/api/v1/customer/forbidden-resource');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    const retryReq = httpMock.expectOne('/api/v1/customer/forbidden-resource');
    retryReq.flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

    expect(authServiceSpy.logout).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
    expect(caughtError.status).toBe(403);
  });

  it('should not log out when retried request returns 500', () => {
    authServiceSpy.getToken.and.returnValue('expired-token');
    authServiceSpy.refreshToken.and.returnValue(of({ success: true, data: { token: 'new-refreshed-token' } }));

    let caughtError: any;
    http.get('/api/v1/customer/server-error').subscribe({
      error: err => caughtError = err
    });

    const firstReq = httpMock.expectOne('/api/v1/customer/server-error');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    const retryReq = httpMock.expectOne('/api/v1/customer/server-error');
    retryReq.flush({ message: 'Internal Server Error' }, { status: 500, statusText: 'Internal Server Error' });

    expect(authServiceSpy.logout).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
    expect(caughtError.status).toBe(500);
  });

  it('should not log out when retried request has network error', () => {
    authServiceSpy.getToken.and.returnValue('expired-token');
    authServiceSpy.refreshToken.and.returnValue(of({ success: true, data: { token: 'new-refreshed-token' } }));

    let caughtError: any;
    http.get('/api/v1/customer/network-fail').subscribe({
      error: err => caughtError = err
    });

    const firstReq = httpMock.expectOne('/api/v1/customer/network-fail');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    const retryReq = httpMock.expectOne('/api/v1/customer/network-fail');
    retryReq.error(new ProgressEvent('error'));

    expect(authServiceSpy.logout).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
    expect(caughtError).toBeTruthy();
  });

  it('should treat refresh response with missing access token as failure and logout', () => {
    authServiceSpy.getToken.and.returnValue('expired-token');
    authServiceSpy.refreshToken.and.returnValue(of({ success: true, data: {} }));

    let caughtError: any;
    http.get('/api/v1/customer/orders').subscribe({
      error: err => caughtError = err
    });

    const firstReq = httpMock.expectOne('/api/v1/customer/orders');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.logout).toHaveBeenCalledWith(false);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/customer/profile' } });
    expect(caughtError).toBeTruthy();
  });

  it('should share a single refresh call among multiple simultaneous 401 requests', () => {
    authServiceSpy.getToken.and.returnValue('expired-token');
    const refreshSubject = new Subject<any>();
    authServiceSpy.refreshToken.and.returnValue(refreshSubject.asObservable());

    let res1: any, res2: any;
    http.get('/api/v1/customer/orders').subscribe(data => res1 = data);
    http.get('/api/v1/customer/cart').subscribe(data => res2 = data);

    const req1 = httpMock.expectOne('/api/v1/customer/orders');
    const req2 = httpMock.expectOne('/api/v1/customer/cart');

    req1.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
    req2.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.refreshToken).toHaveBeenCalledTimes(1);

    refreshSubject.next({ success: true, data: { token: 'shared-new-token' } });
    refreshSubject.complete();

    const retry1 = httpMock.expectOne('/api/v1/customer/orders');
    const retry2 = httpMock.expectOne('/api/v1/customer/cart');
    expect(retry1.request.headers.get('Authorization')).toBe('Bearer shared-new-token');
    expect(retry2.request.headers.get('Authorization')).toBe('Bearer shared-new-token');

    retry1.flush({ orders: [1] });
    retry2.flush({ cart: [2] });

    expect(res1).toEqual({ orders: [1] });
    expect(res2).toEqual({ cart: [2] });
  });
});
