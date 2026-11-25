import { Injectable, signal } from '@angular/core';

export type ToastTone = 'success' | 'info' | 'warning' | 'danger';

export interface Toast {
  id: number;
  tone: ToastTone;
  message: string;
}

/** Duración de un aviso según el manual (4 s). Los errores se quedan hasta cerrarlos. */
const DURATION_MS = 4000;

@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 0;
  private readonly list = signal<Toast[]>([]);
  readonly toasts = this.list.asReadonly();

  show(t: { tone: ToastTone; message: string; persistent?: boolean }): number {
    const id = ++this.nextId;
    this.list.update((all) => [...all, { id, tone: t.tone, message: t.message }]);
    if (!t.persistent && t.tone !== 'danger') setTimeout(() => this.dismiss(id), DURATION_MS);
    return id;
  }

  dismiss(id: number): void {
    this.list.update((all) => all.filter((t) => t.id !== id));
  }
}
