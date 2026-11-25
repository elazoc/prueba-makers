import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';

/** Arranca el simulador de la API en el navegador (solo en `npm run start:mock`). */
export async function startMocks(): Promise<void> {
  await setupWorker(...handlers).start({ onUnhandledFrame: 'bypass', quiet: true });
}
