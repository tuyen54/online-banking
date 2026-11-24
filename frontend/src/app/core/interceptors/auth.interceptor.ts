import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { KeycloakService } from '../auth/keycloak.service';
import { environment } from '../../../environments/environment';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const keycloakService = inject(KeycloakService);

  // Check if target URL belongs to backend API
  const isApiRequest = req.url.startsWith(environment.apiBaseUrl) || req.url.startsWith('/api');

  if (!isApiRequest || !keycloakService.isAuthenticated()) {
    return next(req);
  }

  return from(keycloakService.getToken()).pipe(
    switchMap((token) => {
      const authReq = token
        ? req.clone({
            setHeaders: {
              Authorization: `Bearer ${token}`
            }
          })
        : req;

      return next(authReq).pipe(
        catchError((error: HttpErrorResponse) => {
          if (error.status === 401) {
            console.warn('Backend rejected Bearer token with 401 Unauthorized.');
            // Let the caller handle or trigger login
          } else if (error.status === 403) {
            console.warn('Backend rejected request with 403 Forbidden (RBAC/ABAC denial).');
          }
          return throwError(() => error);
        })
      );
    })
  );
};

