import { TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { MainLayoutComponent } from './main-layout.component';
import { AuthService } from '../../core/auth/auth.service';
import { AuthState } from '../../core/auth/auth.models';

describe('MainLayoutComponent Role-Based Navigation', () => {
  let authServiceMock: {
    currentAuthState: AuthState;
    authState$: any;
    logout: jasmine.Spy;
  };

  beforeEach(async () => {
    authServiceMock = {
      currentAuthState: {
        isAuthenticated: true,
        isInitialized: true,
        keycloakUserId: 'sub-1',
        username: 'test_user',
        email: 'test@test.com',
        displayName: 'Test User',
        roles: ['CUSTOMER'],
        primaryRole: 'CUSTOMER',
        profile: null
      },
      authState$: of({}),
      logout: jasmine.createSpy('logout')
    };

    await TestBed.configureTestingModule({
      imports: [MainLayoutComponent, RouterTestingModule],
      providers: [
        { provide: AuthService, useValue: authServiceMock }
      ]
    }).compileComponents();
  });

  it('should only show CUSTOMER navigation items to customer users', () => {
    authServiceMock.currentAuthState.roles = ['CUSTOMER'];

    const fixture = TestBed.createComponent(MainLayoutComponent);
    const component = fixture.componentInstance;
    const items = component.filteredNavItems.map(i => i.path);

    expect(items).toContain('/customer/dashboard');
    expect(items).toContain('/customer/transfers');
    expect(items).toContain('/customer/accounts');
    expect(items).not.toContain('/admin/dashboard');
    expect(items).not.toContain('/staff/dashboard');
    expect(items).not.toContain('/auditor/dashboard');
  });

  it('should only show ADMIN navigation items and strictly HIDE transfers from ADMIN', () => {
    authServiceMock.currentAuthState.roles = ['ADMIN'];

    const fixture = TestBed.createComponent(MainLayoutComponent);
    const component = fixture.componentInstance;
    const items = component.filteredNavItems.map(i => i.path);

    expect(items).toContain('/admin/dashboard');
    expect(items).not.toContain('/customer/transfers');
    expect(items).not.toContain('/customer/dashboard');
    expect(items).not.toContain('/staff/dashboard');
  });

  it('should only show STAFF navigation items to staff users', () => {
    authServiceMock.currentAuthState.roles = ['STAFF'];

    const fixture = TestBed.createComponent(MainLayoutComponent);
    const component = fixture.componentInstance;
    const items = component.filteredNavItems.map(i => i.path);

    expect(items).toContain('/staff/dashboard');
    expect(items).not.toContain('/customer/transfers');
    expect(items).not.toContain('/admin/dashboard');
  });

  it('should only show AUDITOR navigation items to auditor users', () => {
    authServiceMock.currentAuthState.roles = ['AUDITOR'];

    const fixture = TestBed.createComponent(MainLayoutComponent);
    const component = fixture.componentInstance;
    const items = component.filteredNavItems.map(i => i.path);

    expect(items).toContain('/auditor/dashboard');
    expect(items).not.toContain('/customer/transfers');
    expect(items).not.toContain('/admin/dashboard');
  });
});

