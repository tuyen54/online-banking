import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { KeycloakService } from '../auth/keycloak.service';
import { environment } from '../../../environments/environment';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let keycloakServiceSpy: jasmine.SpyObj<KeycloakService>;

  beforeEach(() => {
    keycloakServiceSpy = jasmine.createSpyObj('KeycloakService', ['isAuthenticated', 'getToken']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: KeycloakService, useValue: keycloakServiceSpy }
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should attach Bearer token to backend API requests', (done) => {
    keycloakServiceSpy.isAuthenticated.and.returnValue(true);
    keycloakServiceSpy.getToken.and.returnValue(Promise.resolve('mock-access-token-xyz'));

    http.get(`${environment.apiBaseUrl}/api/accounts`).subscribe(() => {
      done();
    });

    setTimeout(() => {
      const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/accounts`);
      expect(req.request.headers.has('Authorization')).toBeTrue();
      expect(req.request.headers.get('Authorization')).toBe('Bearer mock-access-token-xyz');
      req.flush([]);
    }, 10);
  });

  it('should NOT attach token to external third-party URLs', () => {
    keycloakServiceSpy.isAuthenticated.and.returnValue(true);

    http.get('https://api.external-service.com/data').subscribe();

    const req = httpMock.expectOne('https://api.external-service.com/data');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });
});

