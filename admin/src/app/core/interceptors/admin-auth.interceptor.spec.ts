import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
import { adminAuthInterceptor } from './admin-auth.interceptor';
import { AdminAuthService } from '../services/admin-auth.service';

describe('AdminAuthInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AdminAuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AdminAuthService', ['getToken', 'refreshToken', 'logout', 'isTokenExpired', 'setToken']);
    authServiceSpy.isTokenExpired.and.returnValue(false);
    routerSpy = jasmine.createSpyObj('Router', ['navigate'], { url: '/admin/orders' });

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([adminAuthInterceptor])),
        provideHttpClientTesting(),
        { provide: AdminAuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should attach Bearer token to standard requests when token exists', () => {
    authServiceSpy.getToken.and.returnValue('mock-admin-access-token');

    http.get('/api/v1/admin/dashboard').subscribe();

    const req = httpMock.expectOne('/api/v1/admin/dashboard');
    expect(req.request.headers.get('Authorization')).toBe('Bearer mock-admin-access-token');
    req.flush({ success: true });
  });

  it('should not attach Authorization header to /auth/refresh request', () => {
    authServiceSpy.getToken.and.returnValue('mock-admin-access-token');

    http.post('/api/v1/auth/refresh', {}).subscribe();

    const req = httpMock.expectOne('/api/v1/auth/refresh');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({ success: true });
  });

  it('should not attach Authorization header when token is null', () => {
    authServiceSpy.getToken.and.returnValue(null);

    http.get('/api/v1/public/ping').subscribe();

    const req = httpMock.expectOne('/api/v1/public/ping');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({ success: true });
  });

  it('should refresh token on 401 and retry request with new Bearer token', () => {
    authServiceSpy.getToken.and.returnValue('expired-admin-token');
    authServiceSpy.refreshToken.and.returnValue(of({
      token: 'new-refreshed-admin-token',
      tokenType: 'Bearer',
      email: 'admin@nova.com',
      firstName: 'Super',
      lastName: 'Admin',
      roles: ['ROLE_SUPER_ADMIN']
    }));

    let responseData: any;
    http.get('/api/v1/admin/analytics').subscribe(data => responseData = data);

    const firstReq = httpMock.expectOne('/api/v1/admin/analytics');
    expect(firstReq.request.headers.get('Authorization')).toBe('Bearer expired-admin-token');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.refreshToken).toHaveBeenCalled();

    const retryReq = httpMock.expectOne('/api/v1/admin/analytics');
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer new-refreshed-admin-token');
    expect(retryReq.request.headers.get('X-Refresh-Retry')).toBe('true');
    retryReq.flush({ success: true, count: 42 });

    expect(responseData).toEqual({ success: true, count: 42 });
  });

  it('should call authService.logout(false) on refresh failure to prevent duplicate logout API calls', () => {
    authServiceSpy.getToken.and.returnValue('expired-admin-token');
    authServiceSpy.refreshToken.and.returnValue(throwError(() => ({ status: 401, message: 'Invalid refresh token' })));

    let hasError = false;
    http.get('/api/v1/admin/analytics').subscribe({
      error: () => hasError = true
    });

    const firstReq = httpMock.expectOne('/api/v1/admin/analytics');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.logout).toHaveBeenCalledWith(false);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/admin/orders' } });
    expect(hasError).toBeTrue();
  });

  it('should retry immediately without calling refreshToken if token in storage is already refreshed and valid', () => {
    authServiceSpy.getToken.and.returnValues('old-token', 'fresh-token');

    let responseData: any;
    http.get('/api/v1/admin/analytics').subscribe(data => responseData = data);

    const firstReq = httpMock.expectOne('/api/v1/admin/analytics');
    expect(firstReq.request.headers.get('Authorization')).toBe('Bearer old-token');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.refreshToken).not.toHaveBeenCalled();

    const retryReq = httpMock.expectOne('/api/v1/admin/analytics');
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer fresh-token');
    expect(retryReq.request.headers.get('X-Refresh-Retry')).toBe('true');
    retryReq.flush({ success: true });

    expect(responseData).toEqual({ success: true });
  });

  it('should prevent retry loops when request already has X-Refresh-Retry', () => {
    authServiceSpy.getToken.and.returnValue('some-token');

    let hasError = false;
    http.get('/api/v1/admin/analytics', {
      headers: { 'X-Refresh-Retry': 'true' }
    }).subscribe({
      error: () => hasError = true
    });

    const req = httpMock.expectOne('/api/v1/admin/analytics');
    req.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.refreshToken).not.toHaveBeenCalled();
    expect(authServiceSpy.logout).toHaveBeenCalledWith(false);
    expect(hasError).toBeTrue();
  });

  it('should preserve original custom headers on stale-token retry', () => {
    authServiceSpy.getToken.and.returnValues('old-token', 'fresh-token');

    http.get('/api/v1/admin/analytics', {
      headers: { 'X-Admin-Client': 'OpsPortal', 'X-Correlation-Id': 'admin-corr-456' }
    }).subscribe();

    const firstReq = httpMock.expectOne('/api/v1/admin/analytics');
    expect(firstReq.request.headers.get('Authorization')).toBe('Bearer old-token');
    expect(firstReq.request.headers.get('X-Admin-Client')).toBe('OpsPortal');
    expect(firstReq.request.headers.get('X-Correlation-Id')).toBe('admin-corr-456');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.refreshToken).not.toHaveBeenCalled();

    const retryReq = httpMock.expectOne('/api/v1/admin/analytics');
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer fresh-token');
    expect(retryReq.request.headers.get('X-Refresh-Retry')).toBe('true');
    expect(retryReq.request.headers.get('X-Admin-Client')).toBe('OpsPortal');
    expect(retryReq.request.headers.get('X-Correlation-Id')).toBe('admin-corr-456');
    retryReq.flush({ success: true });
  });

  it('should preserve original custom headers on local refresh retry', () => {
    authServiceSpy.getToken.and.returnValue('expired-token');
    authServiceSpy.refreshToken.and.returnValue(of({
      token: 'new-refreshed-admin-token',
      tokenType: 'Bearer',
      email: 'admin@nova.com',
      firstName: 'Super',
      lastName: 'Admin',
      roles: ['ROLE_SUPER_ADMIN']
    }));

    http.get('/api/v1/admin/analytics', {
      headers: { 'X-Trace-Id': 'admin-trace-777' }
    }).subscribe();

    const firstReq = httpMock.expectOne('/api/v1/admin/analytics');
    expect(firstReq.request.headers.get('X-Trace-Id')).toBe('admin-trace-777');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    const retryReq = httpMock.expectOne('/api/v1/admin/analytics');
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer new-refreshed-admin-token');
    expect(retryReq.request.headers.get('X-Refresh-Retry')).toBe('true');
    expect(retryReq.request.headers.get('X-Trace-Id')).toBe('admin-trace-777');
    retryReq.flush({ success: true });
  });

  it('should not log out when retried request returns 403', () => {
    authServiceSpy.getToken.and.returnValue('expired-admin-token');
    authServiceSpy.refreshToken.and.returnValue(of({
      token: 'new-refreshed-admin-token',
      tokenType: 'Bearer',
      email: 'admin@nova.com',
      firstName: 'Super',
      lastName: 'Admin',
      roles: ['ROLE_SUPER_ADMIN']
    }));

    let caughtError: any;
    http.get('/api/v1/admin/forbidden-resource').subscribe({
      error: err => caughtError = err
    });

    const firstReq = httpMock.expectOne('/api/v1/admin/forbidden-resource');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    const retryReq = httpMock.expectOne('/api/v1/admin/forbidden-resource');
    retryReq.flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

    expect(authServiceSpy.logout).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
    expect(caughtError.status).toBe(403);
  });

  it('should not log out when retried request returns 500', () => {
    authServiceSpy.getToken.and.returnValue('expired-admin-token');
    authServiceSpy.refreshToken.and.returnValue(of({
      token: 'new-refreshed-admin-token',
      tokenType: 'Bearer',
      email: 'admin@nova.com',
      firstName: 'Super',
      lastName: 'Admin',
      roles: ['ROLE_SUPER_ADMIN']
    }));

    let caughtError: any;
    http.get('/api/v1/admin/server-error').subscribe({
      error: err => caughtError = err
    });

    const firstReq = httpMock.expectOne('/api/v1/admin/server-error');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    const retryReq = httpMock.expectOne('/api/v1/admin/server-error');
    retryReq.flush({ message: 'Internal Server Error' }, { status: 500, statusText: 'Internal Server Error' });

    expect(authServiceSpy.logout).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
    expect(caughtError.status).toBe(500);
  });

  it('should not log out when retried request has network error', () => {
    authServiceSpy.getToken.and.returnValue('expired-admin-token');
    authServiceSpy.refreshToken.and.returnValue(of({
      token: 'new-refreshed-admin-token',
      tokenType: 'Bearer',
      email: 'admin@nova.com',
      firstName: 'Super',
      lastName: 'Admin',
      roles: ['ROLE_SUPER_ADMIN']
    }));

    let caughtError: any;
    http.get('/api/v1/admin/network-fail').subscribe({
      error: err => caughtError = err
    });

    const firstReq = httpMock.expectOne('/api/v1/admin/network-fail');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    const retryReq = httpMock.expectOne('/api/v1/admin/network-fail');
    retryReq.error(new ProgressEvent('error'));

    expect(authServiceSpy.logout).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
    expect(caughtError).toBeTruthy();
  });

  it('should treat refresh response with missing access token as failure and logout', () => {
    authServiceSpy.getToken.and.returnValue('expired-admin-token');
    authServiceSpy.refreshToken.and.returnValue(of({} as any));

    let caughtError: any;
    http.get('/api/v1/admin/analytics').subscribe({
      error: err => caughtError = err
    });

    const firstReq = httpMock.expectOne('/api/v1/admin/analytics');
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.logout).toHaveBeenCalledWith(false);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/admin/orders' } });
    expect(caughtError).toBeTruthy();
  });

  it('should share a single refresh call among multiple simultaneous 401 requests', () => {
    authServiceSpy.getToken.and.returnValue('expired-admin-token');
    const refreshSubject = new Subject<any>();
    authServiceSpy.refreshToken.and.returnValue(refreshSubject.asObservable());

    let res1: any, res2: any;
    http.get('/api/v1/admin/analytics').subscribe(data => res1 = data);
    http.get('/api/v1/admin/settings').subscribe(data => res2 = data);

    const req1 = httpMock.expectOne('/api/v1/admin/analytics');
    const req2 = httpMock.expectOne('/api/v1/admin/settings');

    req1.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
    req2.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authServiceSpy.refreshToken).toHaveBeenCalledTimes(1);

    refreshSubject.next({
      token: 'shared-admin-token',
      tokenType: 'Bearer',
      email: 'admin@nova.com',
      firstName: 'Super',
      lastName: 'Admin',
      roles: ['ROLE_SUPER_ADMIN']
    });
    refreshSubject.complete();

    const retry1 = httpMock.expectOne('/api/v1/admin/analytics');
    const retry2 = httpMock.expectOne('/api/v1/admin/settings');
    expect(retry1.request.headers.get('Authorization')).toBe('Bearer shared-admin-token');
    expect(retry2.request.headers.get('Authorization')).toBe('Bearer shared-admin-token');

    retry1.flush({ stats: 1 });
    retry2.flush({ settings: 2 });

    expect(res1).toEqual({ stats: 1 });
    expect(res2).toEqual({ settings: 2 });
  });
});
