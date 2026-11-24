import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { roleGuard } from './role.guard';
import { KeycloakService } from '../auth/keycloak.service';

describe('roleGuard', () => {
  let keycloakServiceSpy: jasmine.SpyObj<KeycloakService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    keycloakServiceSpy = jasmine.createSpyObj('KeycloakService', ['getRoles']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: KeycloakService, useValue: keycloakServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
  });

  it('should grant access when user has the required role', () => {
    keycloakServiceSpy.getRoles.and.returnValue(['CUSTOMER']);

    const route = { data: { roles: ['CUSTOMER'] } } as any;
    const state = { url: '/customer/dashboard' } as any;

    const result = TestBed.runInInjectionContext(() => roleGuard(route, state));
    expect(result).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  it('should deny access and navigate to /unauthorized when user lacks required role', () => {
    keycloakServiceSpy.getRoles.and.returnValue(['CUSTOMER']);

    const route = { data: { roles: ['ADMIN'] } } as any;
    const state = { url: '/admin/dashboard' } as any;

    const result = TestBed.runInInjectionContext(() => roleGuard(route, state));
    expect(result).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/unauthorized']);
  });

  it('should allow navigation if route specifies no roles restriction', () => {
    keycloakServiceSpy.getRoles.and.returnValue([]);

    const route = { data: {} } as any;
    const state = { url: '/public' } as any;

    const result = TestBed.runInInjectionContext(() => roleGuard(route, state));
    expect(result).toBeTrue();
  });
});

