import { HttpInterceptorFn, HttpErrorResponse, HttpRequest, HttpHandlerFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, Subject, catchError, finalize, first, map, of, shareReplay, switchMap, throwError, timeout } from 'rxjs';
import { AdminAuthService } from '../services/admin-auth.service';

interface AdminAuthChannelMessage {
  type: 'REFRESH_STARTED' | 'REFRESH_SUCCESS' | 'REFRESH_FAILED' | 'LOGOUT';
  token?: string;
}

interface RemoteRefreshResult {
  success: boolean;
  token?: string;
}

let refreshInProgress$: Observable<string> | null = null;
let remoteRefreshSubject$: Subject<RemoteRefreshResult> | null = null;

const authChannel: BroadcastChannel | null = typeof BroadcastChannel !== 'undefined'
  ? new BroadcastChannel('nova_admin_auth_sync')
  : null;

if (authChannel) {
  authChannel.onmessage = (event: MessageEvent<AdminAuthChannelMessage>) => {
    const data = event.data;
    if (!data) return;

    switch (data.type) {
      case 'REFRESH_STARTED':
        if (!refreshInProgress$ && !remoteRefreshSubject$) {
          remoteRefreshSubject$ = new Subject();
        }
        break;
      case 'REFRESH_SUCCESS':
        if (data.token) {
          try {
            localStorage.setItem('nova_admin_token', data.token);
          } catch (_) { }
        }
        if (remoteRefreshSubject$) {
          remoteRefreshSubject$.next({ success: true, token: data.token });
          remoteRefreshSubject$.complete();
          remoteRefreshSubject$ = null;
        }
        break;
      case 'REFRESH_FAILED':
        if (remoteRefreshSubject$) {
          remoteRefreshSubject$.next({ success: false });
          remoteRefreshSubject$.complete();
          remoteRefreshSubject$ = null;
        }
        break;
      case 'LOGOUT':
        try {
          localStorage.removeItem('nova_admin_token');
          localStorage.removeItem('nova_admin_user');
        } catch (_) { }
        if (remoteRefreshSubject$) {
          remoteRefreshSubject$.next({ success: false });
          remoteRefreshSubject$.complete();
          remoteRefreshSubject$ = null;
        }
        break;
    }
  };
}

function addTokenHeader(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`
    }
  });
}

function executeLocalRefresh(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  authService: AdminAuthService,
  router: Router
): Observable<any> {
  if (!refreshInProgress$) {
    try {
      authChannel?.postMessage({ type: 'REFRESH_STARTED' });
    } catch (_) { }

    refreshInProgress$ = authService.refreshToken().pipe(
      map(res => {
        const newToken = res?.token || (res as any)?.data?.token;
        if (!newToken) {
          throw new Error('No access token in refresh response');
        }
        try {
          authChannel?.postMessage({ type: 'REFRESH_SUCCESS', token: newToken });
        } catch (_) { }
        return newToken;
      }),
      catchError(err => {
        try {
          authChannel?.postMessage({ type: 'REFRESH_FAILED' });
        } catch (_) { }
        authService.logout(false);
        const currentUrl = router.url;
        if (currentUrl && !currentUrl.includes('/login')) {
          router.navigate(['/login'], { queryParams: { returnUrl: currentUrl } });
        }
        return throwError(() => err);
      }),
      finalize(() => {
        refreshInProgress$ = null;
      }),
      shareReplay({ bufferSize: 1, refCount: false })
    );
  }

  return refreshInProgress$.pipe(
    switchMap(newToken => {
      const retryReq = req.clone({
        setHeaders: {
          Authorization: `Bearer ${newToken}`,
          'X-Refresh-Retry': 'true'
        }
      });
      return next(retryReq);
    })
  );
}

export const adminAuthInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AdminAuthService);
  const router = inject(Router);
  const token = authService.getToken();

  let modifiedReq = req;
  if (token && !req.headers.has('Authorization') && !req.url.includes('/auth/refresh')) {
    modifiedReq = addTokenHeader(req, token);
  }

  return next(modifiedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // Prevent refresh loops if retried request or auth endpoints fail with 401
        if (
          modifiedReq.headers.has('X-Refresh-Retry') ||
          modifiedReq.url.includes('/auth/login') ||
          modifiedReq.url.includes('/auth/refresh') ||
          modifiedReq.url.includes('/auth/logout')
        ) {
          if (modifiedReq.headers.has('X-Refresh-Retry') || modifiedReq.url.includes('/auth/refresh')) {
            authService.logout(false);
            try {
              authChannel?.postMessage({ type: 'REFRESH_FAILED' });
            } catch (_) { }
            const currentUrl = router.url;
            if (currentUrl && !currentUrl.includes('/login')) {
              router.navigate(['/login'], { queryParams: { returnUrl: currentUrl } });
            }
          }
          return throwError(() => error);
        }

        // Stale-token retry check: if authService has a newer valid token than the one attached to this request, retry immediately
        const latestToken = authService.getToken();
        const reqAuthHeader = modifiedReq.headers.get('Authorization');
        const reqToken = reqAuthHeader?.startsWith('Bearer ') ? reqAuthHeader.substring(7) : null;
        if (latestToken && reqToken && latestToken !== reqToken && !authService.isTokenExpired(latestToken)) {
          const retryReq = modifiedReq.clone({
            setHeaders: {
              Authorization: `Bearer ${latestToken}`,
              'X-Refresh-Retry': 'true'
            }
          });
          return next(retryReq);
        }

        // Remote tab refresh synchronization: wait on remote tab refresh if one is already active
        if (remoteRefreshSubject$) {
          return remoteRefreshSubject$.asObservable().pipe(
            first(),
            timeout({
              each: 10000,
              with: () => {
                remoteRefreshSubject$ = null;
                return of<RemoteRefreshResult>({ success: false });
              }
            }),
            switchMap((result: RemoteRefreshResult) => {
              if (result.success && result.token) {
                const retryReq = modifiedReq.clone({
                  setHeaders: {
                    Authorization: `Bearer ${result.token}`,
                    'X-Refresh-Retry': 'true'
                  }
                });
                return next(retryReq);
              }
              // If remote tab refresh failed or timed out, attempt local refresh
              return executeLocalRefresh(modifiedReq, next, authService, router);
            })
          );
        }

        return executeLocalRefresh(modifiedReq, next, authService, router);
      }

      return throwError(() => error);
    })
  );
};
