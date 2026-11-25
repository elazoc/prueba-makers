/**
 * El simulador no puede poner una cookie HttpOnly de verdad. Guarda el refresh token aquí,
 * compartido entre pestañas como lo estaría la cookie. La app nunca lee esta clave.
 */
const COOKIE_KEY = 'prestemos-mock-refresh-cookie';

export const cookieJar = {
  get(): string | null {
    return localStorage.getItem(COOKIE_KEY);
  },
  set(token: string): void {
    localStorage.setItem(COOKIE_KEY, token);
  },
  clear(): void {
    localStorage.removeItem(COOKIE_KEY);
  },
};
