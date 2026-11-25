import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PrButton } from './button';

@Component({
  imports: [PrButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<button prButton variant="primary" [loading]="loading()" loadingText="Aprobando…" (click)="clicks = clicks + 1">
    Aprobar
  </button>`,
})
class Host {
  loading = signal(false);
  clicks = 0;
}

describe('PrButton', () => {
  async function render() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const button = (fixture.nativeElement as HTMLElement).querySelector('button')!;
    return { fixture, button };
  }

  it('applies the variant and size classes', async () => {
    const { button } = await render();
    expect(button.classList).toContain('pr-btn');
    expect(button.classList).toContain('pr-btn--primary');
    expect(button.classList).toContain('pr-btn--md');
    expect(button.textContent?.trim()).toBe('Aprobar');
  });

  it('shows the loading text, marks itself busy and ignores clicks while loading', async () => {
    const { fixture, button } = await render();
    fixture.componentInstance.loading.set(true);
    await fixture.whenStable();

    expect(button.getAttribute('aria-busy')).toBe('true');
    expect(button.getAttribute('aria-disabled')).toBe('true');
    expect(button.textContent).toContain('Aprobando…');
    expect(button.querySelector('pr-spinner')).not.toBeNull();

    button.click();
    expect(fixture.componentInstance.clicks).toBe(0);
  });

  it('clicks normally when not loading', async () => {
    const { fixture, button } = await render();
    button.click();
    expect(fixture.componentInstance.clicks).toBe(1);
    expect(button.hasAttribute('aria-busy')).toBe(false);
  });
});
