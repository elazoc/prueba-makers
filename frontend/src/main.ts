import { bootstrapApplication } from '@angular/platform-browser';
import { startMocks } from './environments/mocks-loader';
import { appConfig } from './app/app.config';
import { App } from './app/app';

async function bootstrap(): Promise<void> {
  // Solo hace algo en la configuración `mock`: el simulador debe estar listo antes de la primera petición.
  await startMocks();
  await bootstrapApplication(App, appConfig);
}

bootstrap().catch((err) => console.error(err));
