import { toPage } from './page';

describe('toPage', () => {
  it('adapts the backend page to the app page', () => {
    expect(toPage({ content: [1], page: 2, size: 20, totalElements: 41, totalPages: 3 })).toEqual({
      items: [1],
      page: 2,
      size: 20,
      total: 41,
      totalPages: 3,
    });
  });
});
