import { formatMoney, formatMoneyDraft, parseMoneyInput, toCents } from './money';
import { MoneyPipe } from './money.pipe';

describe('parseMoneyInput', () => {
  it.each([
    ['1500000', '1500000.00'],
    ['1.500.000', '1500000.00'],
    ['$ 1.500.000', '1500000.00'],
    ['$\u00a01.500.000', '1500000.00'],
    ['1.500.000,5', '1500000.50'],
    ['1500000,25', '1500000.25'],
    [' 100 ', '100.00'],
    ['0005', '5.00'],
  ])('accepts %s as %s', (input, value) => {
    expect(parseMoneyInput(input)).toEqual({ ok: true, value });
  });

  it.each([
    ['', 'empty'],
    ['   ', 'empty'],
    ['abc', 'format'],
    ['1,500,000.00', 'format'],
    ['1.50', 'format'],
    ['10,555', 'decimals'],
    ['-5', 'format'],
    ['1.5000', 'format'],
  ])('rejects %s as %s', (input, reason) => {
    expect(parseMoneyInput(input)).toEqual({ ok: false, reason });
  });
});

describe('formatMoney', () => {
  it('groups thousands and hides zero cents', () => {
    expect(formatMoney('1500000.00')).toBe('$\u00a01.500.000');
    expect(formatMoney('100.00')).toBe('$\u00a0100');
  });

  it('shows cents when they are not zero', () => {
    expect(formatMoney('1500000.50')).toBe('$\u00a01.500.000,50');
  });
});

describe('formatMoneyDraft', () => {
  it('groups while typing and keeps a trailing comma', () => {
    expect(formatMoneyDraft('1500000')).toBe('1.500.000');
    expect(formatMoneyDraft('1500000,')).toBe('1.500.000,');
    expect(formatMoneyDraft('1.500.0001')).toBe('15.000.001');
    expect(formatMoneyDraft('$ 2500,5')).toBe('2.500,5');
  });
});

describe('toCents', () => {
  it('never loses precision', () => {
    expect(toCents('49999999999999.99')).toBe(4999999999999999n);
    expect(toCents('100.00')).toBe(10000n);
  });
});

describe('MoneyPipe', () => {
  it('formats or returns empty', () => {
    const pipe = new MoneyPipe();
    expect(pipe.transform('800000.00')).toBe('$\u00a0800.000');
    expect(pipe.transform(null)).toBe('');
  });
});
