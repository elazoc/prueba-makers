import { DOCUMENT } from '@angular/common';
import { Injectable, effect, inject, signal } from '@angular/core';

export type ThemeMode = 'system' | 'light' | 'dark';

const KEY = 'prestemos-theme';

/** Tema claro u oscuro: sigue al sistema y se puede fijar desde el menú del usuario. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly media =
    typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: dark)') : null;
  private readonly systemDark = signal(this.media?.matches ?? false);

  readonly mode = signal<ThemeMode>(readMode());

  constructor() {
    this.media?.addEventListener('change', (e) => this.systemDark.set(e.matches));
    effect(() => {
      const mode = this.mode();
      const dark = mode === 'dark' || (mode === 'system' && this.systemDark());
      this.document.documentElement.classList.toggle('dark', dark);
    });
  }

  setMode(mode: ThemeMode): void {
    this.mode.set(mode);
    localStorage.setItem(KEY, mode);
  }
}

function readMode(): ThemeMode {
  const saved = typeof localStorage === 'undefined' ? null : localStorage.getItem(KEY);
  return saved === 'light' || saved === 'dark' ? saved : 'system';
}
