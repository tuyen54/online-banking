import { TestBed } from '@angular/core/testing';
import { KeycloakService } from './keycloak.service';

describe('KeycloakService', () => {
  let service: KeycloakService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [KeycloakService]
    });
    service = TestBed.inject(KeycloakService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should extract recognized uppercase roles from realm_access', () => {
    // Mock tokenParsed
    (service as any).keycloakInstance = {
      tokenParsed: {
        sub: 'mock-sub-123',
        preferred_username: 'customer01',
        realm_access: {
          roles: ['customer', 'default-roles-online_banking', 'offline_access']
        }
      },
      authenticated: true
    };

    const roles = service.getRoles();
    expect(roles).toEqual(['CUSTOMER']);
    expect(service.hasRole('CUSTOMER')).toBeTrue();
    expect(service.hasRole('ADMIN')).toBeFalse();
    expect(service.getPrimaryRole()).toBe('CUSTOMER');
  });

  it('should prioritize ADMIN as primary role when present', () => {
    (service as any).keycloakInstance = {
      tokenParsed: {
        sub: 'mock-admin',
        realm_access: {
          roles: ['admin', 'auditor']
        }
      },
      authenticated: true
    };

    expect(service.getPrimaryRole()).toBe('ADMIN');
  });

  it('should handle unauthenticated state safely', () => {
    (service as any).keycloakInstance = {
      authenticated: false,
      tokenParsed: null
    };

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.getRoles()).toEqual([]);
    expect(service.getPrimaryRole()).toBeNull();
    expect(service.getKeycloakUserId()).toBeNull();
  });
});

