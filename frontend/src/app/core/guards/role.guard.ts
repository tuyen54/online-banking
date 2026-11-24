import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { KeycloakService } from '../auth/keycloak.service';
import { UserRole } from '../auth/auth.models';

export const roleGuard: CanActivateFn = (route, state) => {
  const keycloakService = inject(KeycloakService);
  const router = inject(Router);

  const requiredRoles = route.data?.['roles'] as UserRole[] | undefined;

  if (!requiredRoles || requiredRoles.length === 0) {
    return true;
  }

  const userRoles = keycloakService.getRoles();
  const hasRequiredRole = requiredRoles.some((r) => userRoles.includes(r));

  if (hasRequiredRole) {
    return true;
  }

  console.warn(`Access denied to ${state.url}: user lacks required roles [${requiredRoles.join(', ')}]`);
  router.navigate(['/unauthorized']);
  return false;
};

