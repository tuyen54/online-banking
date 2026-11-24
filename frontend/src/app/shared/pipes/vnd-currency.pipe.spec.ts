import { VndCurrencyPipe } from './vnd-currency.pipe';

describe('VndCurrencyPipe', () => {
  const pipe = new VndCurrencyPipe();

  it('should format numbers into VND currency format', () => {
    const formatted = pipe.transform(1000000);
    // Vi-VN currency format contains 1.000.000 and ₫
    expect(formatted).toContain('1.000.000');
    expect(formatted).toContain('₫');
  });

  it('should handle zero, null and empty gracefully', () => {
    expect(pipe.transform(0)).toContain('0');
    expect(pipe.transform(null)).toContain('0');
    expect(pipe.transform(undefined)).toContain('0');
    expect(pipe.transform('')).toContain('0');
  });

  it('should handle numeric string inputs', () => {
    const formatted = pipe.transform('500000');
    expect(formatted).toContain('500.000');
  });
});

