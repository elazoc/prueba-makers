import { Injectable } from '@angular/core';
import { LoginResponse } from '../api/dto';

interface LockManagerLike {
  request<T>(name: string, callback: () => Promise<T>): Promise<T>;
}

/**
 * Una sola renovación a la vez. Dentro de la pestaña, quienes piden a la vez comparten la misma
 * promesa. Entre pestañas, la Web Locks API las pone en fila: el refresh token rota en cada uso y
 * la cookie es compartida, así que dos renovaciones simultáneas harían que el backend viera un
 * token ya usado y revocara la sesión.
 */
@Injectable({ providedIn: 'root' })
export class RefreshCoordinator {
  private inFlight: Promise<LoginResponse> | null = null;

  run(task: () => Promise<LoginResponse>): Promise<LoginResponse> {
    if (this.inFlight) return this.inFlight;

    const locks = (navigator as Navigator & { locks?: LockManagerLike }).locks;
    const started = locks ? locks.request('prestemos-refresh', task) : task();
    this.inFlight = started.finally(() => (this.inFlight = null));
    return this.inFlight;
  }
}
