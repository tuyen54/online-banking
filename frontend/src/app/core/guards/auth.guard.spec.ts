import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { authGuard } from './auth.guard';
import { KeycloakService } from '../auth/keycloak.service';

describe('authGuard', () => {
  let keycloakServiceSpy: jasmine.SpyObj<KeycloakService>;
  let router: Router;

  beforeEach(() => {
    keycloakServiceSpy = jasmine.createSpyObj('KeycloakService', ['isAuthenticated', 'login']);

    TestBed.configureTestingModule({
      providers: [
        { provide: KeycloakService, useValue: keycloakServiceSpy }
      ]
    });

    router = TestBed.inject(Router);
  });

  it('should allow access if user is authenticated', async () => {
    keycloakServiceSpy.isAuthenticated.and.returnValue(true);

    const result = await TestBed.runInInjectionContext(() =>
      authGuard({} as any, { url: '/customer/dashboard' } as any)
    );

    expect(result).toBeTrue();
    expect(keycloakServiceSpy.login).not.toHaveBeenCalled();
  });

  it('should redirect to Keycloak login if user is not authenticated', async () => {
    keycloakServiceSpy.isAuthenticated.and.returnValue(false);
    keycloakServiceSpy.login.and.returnValue(Promise.resolve());

    const result = await TestBed.runInInjectionContext(() =>
      authGuard({} as any, { url: '/customer/accounts' } as any)
    );

    expect(result).toBeFalse();
    expect(keycloakServiceSpy.login).toHaveBeenCalledWith(window.location.origin + '/customer/accounts');
  });
});

