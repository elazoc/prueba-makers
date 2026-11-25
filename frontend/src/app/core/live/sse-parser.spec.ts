import { SseMessage, createSseParser } from './sse-parser';

describe('createSseParser', () => {
  it('joins events split across chunks, ignores comments and joins multiline data', () => {
    const got: SseMessage[] = [];
    const feed = createSseParser((m) => got.push(m));

    feed('event: loan.approved\nid: e1\ndata: {"a":');
    feed('1}\n\n: ping\n\ndata: x\r\ndata: y\r\n\r\n');

    expect(got).toEqual([{ event: 'loan.approved', id: 'e1', data: '{"a":1}' }, { data: 'x\ny' }]);
  });

  it('ignores blocks without data', () => {
    const got: SseMessage[] = [];
    const feed = createSseParser((m) => got.push(m));
    feed(': conectado\n\nevent: loan.requested\n\n');
    expect(got).toEqual([]);
  });
});
