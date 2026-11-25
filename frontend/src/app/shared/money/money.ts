/**
 * Montos de dinero como texto decimal ("1500000.00"), igual que en la API.
 * Nunca se convierten a `number`: se parsean y comparan como texto o BigInt.
 */

export type MoneyParse =
  | { ok: true; value: string }
  | { ok: false; reason: 'empty' | 'format' | 'decimals' };

const NBSP = '\u00a0';

/** Formato colombiano: miles con punto y decimales con coma. */
const GROUPED = /^\d{1,3}(?:\.\d{3})+(?:,(\d+))?$/;
const PLAIN = /^\d+(?:,(\d+))?$/;

/** Convierte lo que alguien escribe ("$ 1.500.000,5") al texto de la API ("1500000.50"). */
export function parseMoneyInput(text: string): MoneyParse {
  const cleaned = text.replace(/[\s\u00a0]/g, '').replace(/^\$/, '');
  if (cleaned === '') return { ok: false, reason: 'empty' };

  const match = GROUPED.exec(cleaned) ?? PLAIN.exec(cleaned);
  if (!match) return { ok: false, reason: 'format' };

  const decimals = match[1] ?? '';
  if (decimals.length > 2) return { ok: false, reason: 'decimals' };

  const [rawInteger] = cleaned.split(',');
  const integer = rawInteger.replace(/\./g, '').replace(/^0+(?=\d)/, '');
  return { ok: true, value: `${integer}.${decimals.padEnd(2, '0')}` };
}

/** "1500000.00" → "$ 1.500.000"; los centavos solo aparecen si no son cero. */
export function formatMoney(amount: string): string {
  const [integer, decimals = '00'] = amount.split('.');
  const cents = decimals.padEnd(2, '0');
  return `$${NBSP}${groupThousands(integer)}${cents === '00' ? '' : `,${cents}`}`;
}

/** Agrupa los miles mientras se escribe, sin tocar la parte decimal en curso. */
export function formatMoneyDraft(text: string): string {
  const kept = text.replace(/[^\d,]/g, '');
  const commaAt = kept.indexOf(',');
  const integerDigits = (commaAt === -1 ? kept : kept.slice(0, commaAt)).replace(
    /^0+(?=\d)/,
    '',
  );
  const grouped = groupThousands(integerDigits);
  if (commaAt === -1) return grouped;
  return `${grouped},${kept.slice(commaAt + 1).replace(/,/g, '')}`;
}

/** "1500000.50" → 150000050n. Sirve para comparar montos sin perder precisión. */
export function toCents(amount: string): bigint {
  const [integer, decimals = ''] = amount.split('.');
  return BigInt(integer) * 100n + BigInt(decimals.padEnd(2, '0').slice(0, 2));
}

function groupThousands(digits: string): string {
  return digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}
