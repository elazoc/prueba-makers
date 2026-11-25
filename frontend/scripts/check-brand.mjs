// Revisa las reglas de marca que se pueden comprobar en el código.
// Falla (código 1) y lista archivo:línea por cada hallazgo.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const skipDirs = new Set([
  'node_modules',
  'dist',
  '.angular',
  'coverage',
  'test-results',
  'playwright-report',
  '.shots',
]);
const textExt = /\.(ts|mjs|js|css|html|json|md)$/;

// Se arma por partes para que este archivo no se marque a sí mismo.
const forbiddenName = new RegExp(['cae', 'lum'].join(''), 'i');
const hexColor = /(^|[\s:(,'"`])#(?:[0-9a-f]{8}|[0-9a-f]{6}|[0-9a-f]{3,4})\b/i;

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    if (skipDirs.has(name)) continue;
    const full = join(dir, name);
    if (statSync(full).isDirectory()) walk(full, out);
    else if (textExt.test(name)) out.push(full);
  }
  return out;
}

const findings = [];
const report = (file, line, rule) => findings.push(`${file}:${line}  ${rule}`);

for (const full of walk(root)) {
  const file = relative(root, full).split(sep).join('/');
  if (file === 'package-lock.json') continue;
  const isStyleOrCode = /\.(css|ts)$/.test(file);
  const hexAllowed =
    file === 'src/styles/prestemos-tokens.css' ||
    file.startsWith('src/mocks/') ||
    file.startsWith('src/mock-assets/') ||
    file.startsWith('public/') ||
    file.startsWith('scripts/');
  let selector = '';

  readFileSync(full, 'utf8')
    .split('\n')
    .forEach((text, i) => {
      const line = i + 1;
      if (forbiddenName.test(text)) report(file, line, 'nombre de la marca de origen');
      if (!isStyleOrCode) return;

      if (text.includes('{')) selector = text.slice(0, text.indexOf('{'));

      const weight = text.match(/font-weight\s*:\s*([^;'"]+)/);
      if (weight && !/^(400|500|600|inherit)$/.test(weight[1].trim())) {
        report(file, line, `font-weight ${weight[1].trim()} (solo 400, 500 o 600)`);
      }
      if (/backdrop-filter/.test(text)) report(file, line, 'backdrop-filter (sin vidrio)');
      if (/(linear|radial|conic)-gradient\(/.test(text) && !text.includes('brand-ok')) {
        report(file, line, 'degradado sin comentario brand-ok');
      }
      if (!hexAllowed && hexColor.test(text)) report(file, line, 'color literal (usa un token)');
      if (
        /border-radius/.test(text) &&
        /btn/.test(selector) &&
        /(999|50%|radius-full)/.test(text)
      ) {
        report(file, line, 'botón en píldora o círculo');
      }
    });
}

if (findings.length) {
  console.error(`Reglas de marca: ${findings.length} hallazgo(s)\n` + findings.join('\n'));
  process.exit(1);
}
console.log('Reglas de marca: sin hallazgos.');
