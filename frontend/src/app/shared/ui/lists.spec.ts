import { TestBed } from '@angular/core/testing';
import { Page } from '../../core/api/page';
import { PrFilterTabs } from './filter-tabs';
import { PrPagination } from './pagination';
import { PrSkeleton } from './skeleton';

const page = (p: number, total: number, size = 20): Page<number> => ({
  items: [],
  page: p,
  size,
  total,
  totalPages: Math.ceil(total / size),
});

describe('PrPagination', () => {
  async function render(value: Page<number>) {
    const fixture = TestBed.createComponent(PrPagination);
    fixture.componentRef.setInput('page', value);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const button = (name: string) =>
      [...el.querySelectorAll('button')].find((b) => b.textContent?.includes(name))!;
    return { fixture, el, button };
  }

  it('says which rows are shown and disables the edges', async () => {
    const { el, button } = await render(page(0, 57));
    expect(el.textContent).toContain('1–20 de 57');
    expect(button('Anterior').disabled).toBe(true);
    expect(button('Siguiente').disabled).toBe(false);
  });

  it('emits the next page', async () => {
    const { fixture, button } = await render(page(1, 57));
    const emitted: number[] = [];
    fixture.componentInstance.pageChange.subscribe((p) => emitted.push(p));
    button('Siguiente').click();
    expect(emitted).toEqual([2]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('21–40 de 57');
  });

  it('renders nothing when there are no rows', async () => {
    const { el } = await render(page(0, 0));
    expect(el.textContent?.trim()).toBe('');
  });
});

describe('PrFilterTabs', () => {
  it('marks the selected option and changes it on click', async () => {
    const fixture = TestBed.createComponent(PrFilterTabs<string>);
    fixture.componentRef.setInput('options', [
      { value: 'ALL', label: 'Todos' },
      { value: 'PENDING', label: 'Pendientes' },
    ]);
    fixture.componentRef.setInput('value', 'ALL');
    fixture.componentRef.setInput('label', 'Filtrar por estado');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const pending = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Pendientes'))!;

    expect(el.querySelector('[role="group"]')?.getAttribute('aria-label')).toBe('Filtrar por estado');
    expect(pending.getAttribute('aria-pressed')).toBe('false');
    pending.click();
    await fixture.whenStable();
    expect(pending.getAttribute('aria-pressed')).toBe('true');
    expect(fixture.componentInstance.value()).toBe('PENDING');
  });
});

describe('PrSkeleton', () => {
  it('shows busy placeholder rows with a text for screen readers', async () => {
    const fixture = TestBed.createComponent(PrSkeleton);
    fixture.componentRef.setInput('rows', 3);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.getAttribute('aria-busy')).toBe('true');
    expect(el.querySelectorAll('.pr-skeleton__row')).toHaveLength(3);
    expect(el.textContent).toContain('Cargando…');
  });
});
