import { paginate, sortBy } from './paging';

const problem = (status: number, code: string) => expect.objectContaining({ status, code });

describe('mock paging', () => {
  const items = Array.from({ length: 45 }, (_, i) => i);

  it('slices pages with defaults', () => {
    expect(paginate(items, {})).toMatchObject({ page: 0, size: 20, totalElements: 45, totalPages: 3 });
    expect(paginate(items, { page: 2, size: 20 }).content).toEqual([40, 41, 42, 43, 44]);
  });

  it('rejects page sizes out of range', () => {
    expect(() => paginate([], { size: 101 })).toThrow(problem(400, 'VALIDATION_ERROR'));
    expect(() => paginate([], { size: 0 })).toThrow(problem(400, 'VALIDATION_ERROR'));
    expect(() => paginate([], { page: -1 })).toThrow(problem(400, 'VALIDATION_ERROR'));
  });

  it('sorts by allowed fields, comparing amounts exactly', () => {
    const rows = [{ amount: '900.00' }, { amount: '10000.00' }, { amount: '1500.50' }];
    expect(sortBy(rows, 'amount,desc', ['amount'], 'amount,asc').map((r) => r.amount)).toEqual([
      '10000.00',
      '1500.50',
      '900.00',
    ]);
    expect(sortBy(rows, undefined, ['amount'], 'amount,asc')[0].amount).toBe('900.00');
    expect(() => sortBy(rows, 'password,asc', ['amount'], 'amount,asc')).toThrow(
      problem(400, 'VALIDATION_ERROR'),
    );
  });
});
