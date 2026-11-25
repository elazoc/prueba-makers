// Capturas para revisar la interfaz en computador (1440) y celular (375), en claro y oscuro.
// Uso: npm run shots -- --role user|admin|none --routes /prestamos,/no-existe [--base http://localhost:4200]
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';

const args = Object.fromEntries(
  process.argv
    .slice(2)
    .join(' ')
    .split('--')
    .filter(Boolean)
    .map((pair) => pair.trim().split(/\s+/)),
);
const base = args.base ?? 'http://localhost:4200';
const role = args.role ?? 'none';
const routes = (args.routes ?? '/').split(',');
const credentials = {
  user: { email: 'usuario@test.com', password: '123' },
  admin: { email: 'admin@test.com', password: '123' },
};
const outDir = fileURLToPath(new URL('../.shots/', import.meta.url));
mkdirSync(outDir, { recursive: true });

const viewports = [
  { name: '1440', width: 1440, height: 900, isMobile: false },
  { name: '375', width: 375, height: 812, isMobile: true },
];

const browser = await chromium.launch();
for (const theme of ['light', 'dark']) {
  for (const vp of viewports) {
    const context = await browser.newContext({
      viewport: { width: vp.width, height: vp.height },
      deviceScaleFactor: 2,
      isMobile: vp.isMobile,
      hasTouch: vp.isMobile,
    });
    await context.addInitScript((t) => localStorage.setItem('prestemos-theme', t), theme);
    const page = await context.newPage();
    await page.goto(base);
    // La app arranca después del simulador (si lo hay): con ella lista, la API ya responde.
    await page.waitForSelector('app-root router-outlet', { state: 'attached' });

    if (credentials[role]) {
      // Inicia sesión por la API: deja la cookie (o la del simulador) y la app restaura la sesión.
      await page.evaluate(async (c) => {
        await fetch('/api/v1/auth/login', {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'prestemos' },
          body: JSON.stringify(c),
        });
      }, credentials[role]);
    }

    for (const route of routes) {
      await page.goto(base + route);
      await page.waitForLoadState('networkidle').catch(() => undefined);
      await page.waitForTimeout(600);
      const slug = route.replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '') || 'inicio';
      const file = `${outDir}${role}-${slug}-${vp.name}-${theme}.png`;
      await page.screenshot({ path: file, fullPage: true });
      console.log(file);
    }
    await context.close();
  }
}
await browser.close();
