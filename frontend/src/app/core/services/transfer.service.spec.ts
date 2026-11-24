import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TransferService } from './transfer.service';
import { TransferRequest } from '../models/transfer.model';
import { environment } from '../../../environments/environment';

describe('TransferService', () => {
  let service: TransferService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [TransferService]
    });

    service = TestBed.inject(TransferService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should generate distinct idempotency keys', () => {
    const key1 = service.generateIdempotencyKey();
    const key2 = service.generateIdempotencyKey();

    expect(key1).toBeTruthy();
    expect(key2).toBeTruthy();
    expect(key1).not.toEqual(key2);
  });

  it('should send Idempotency-Key in HTTP headers on transfer submission', () => {
    const mockRequest: TransferRequest = {
      sourceAccountId: '00000000-0000-0000-0000-000000000001',
      destinationAccountNumber: '100222222222',
      amount: 500000,
      description: 'Test transfer'
    };
    const mockKey = 'idemp-uuid-12345';

    service.executeTransfer(mockRequest, mockKey).subscribe((response) => {
      expect(response.status).toBe('COMPLETED');
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/transfers`);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe(mockKey);
    expect(req.request.body).toEqual(mockRequest);

    req.flush({
      transactionReference: 'TRF-TEST-001',
      status: 'COMPLETED',
      amount: 500000,
      currency: 'VND',
      sourceAccountNumber: '******0001',
      destinationAccountNumber: '******2222',
      completedAt: new Date().toISOString()
    });
  });
});

