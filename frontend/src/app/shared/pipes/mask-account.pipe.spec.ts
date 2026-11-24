import { MaskAccountPipe } from './mask-account.pipe';

describe('MaskAccountPipe', () => {
  const pipe = new MaskAccountPipe();

  it('should mask 12-digit account numbers leaving last 4 digits visible', () => {
    expect(pipe.transform('100123456789')).toBe('******6789');
  });

  it('should return already-masked numbers unchanged', () => {
    expect(pipe.transform('******1234')).toBe('******1234');
  });

  it('should handle short or empty numbers gracefully', () => {
    expect(pipe.transform('')).toBe('******');
    expect(pipe.transform(null)).toBe('******');
    expect(pipe.transform('123')).toBe('******123');
  });
});

