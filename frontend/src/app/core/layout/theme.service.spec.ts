import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  afterEach(() => {
    localStorage.removeItem('prestemos-theme');
    document.documentElement.classList.remove('dark');
  });

  it('follows the system by default', () => {
    expect(TestBed.inject(ThemeService).mode()).toBe('system');
  });

  it('applies and remembers the dark theme', () => {
    const theme = TestBed.inject(ThemeService);
    theme.setMode('dark');
    TestBed.tick();
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(localStorage.getItem('prestemos-theme')).toBe('dark');

    theme.setMode('light');
    TestBed.tick();
    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });

  it('restores the remembered mode', () => {
    localStorage.setItem('prestemos-theme', 'dark');
    expect(TestBed.inject(ThemeService).mode()).toBe('dark');
  });
});
