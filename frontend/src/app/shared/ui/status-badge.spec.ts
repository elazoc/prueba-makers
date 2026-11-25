import { TestBed } from '@angular/core/testing';
import { LoanStatus } from '../../core/api/dto';
import { PrStatusBadge } from './status-badge';
import { PrIcon } from './icon';

describe('PrStatusBadge', () => {
  it.each<[LoanStatus, string, string]>([
    ['PENDING', 'Pendiente', 'pr-badge--warning'],
    ['APPROVED', 'Aprobado', 'pr-badge--success'],
    ['REJECTED', 'Rechazado', 'pr-badge--danger'],
    ['CANCELLED', 'Cancelado', 'pr-badge--neutral'],
  ])('%s says %s with a word, not only a color', async (status, text, tone) => {
    const fixture = TestBed.createComponent(PrStatusBadge);
    fixture.componentRef.setInput('status', status);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent?.trim()).toBe(text);
    expect(el.querySelector('.pr-badge')?.classList).toContain(tone);
    expect(el.querySelector('pr-icon svg')).not.toBeNull();
  });
});

describe('PrIcon', () => {
  it('draws a decorative lucide icon with the brand stroke', async () => {
    const fixture = TestBed.createComponent(PrIcon);
    fixture.componentRef.setInput('name', 'check');
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    const svg = host.querySelector('svg')!;
    expect(svg.getAttribute('stroke-width')).toBe('1.75');
    expect(svg.querySelector('path')).not.toBeNull();
    expect(host.getAttribute('aria-hidden')).toBe('true');
  });

  it('can carry an accessible label', async () => {
    const fixture = TestBed.createComponent(PrIcon);
    fixture.componentRef.setInput('name', 'search');
    fixture.componentRef.setInput('label', 'Buscar');
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.getAttribute('role')).toBe('img');
    expect(host.getAttribute('aria-label')).toBe('Buscar');
  });
});
