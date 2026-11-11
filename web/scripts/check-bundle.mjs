// Fails the build when the query panel's lazy chunk outgrows its budget, so the engine's weight is a
// decision, not a surprise. Measured gzipped, as the browser receives it.
import { readdirSync, readFileSync } from 'node:fs';
import { gzipSync } from 'node:zlib';

const BUDGET_KB = 400;
const assets = readdirSync('dist/assets');
const chunk = assets.find((name) => /^QueryPanel-.*\.js$/.test(name));
if (!chunk) {
  console.error('check-bundle: no QueryPanel chunk in dist/assets; the query panel must stay a separate, lazily loaded chunk');
  process.exit(1);
}
const kb = gzipSync(readFileSync(`dist/assets/${chunk}`)).length / 1024;
console.log(`check-bundle: ${chunk} is ${kb.toFixed(0)} KB gzipped (budget ${BUDGET_KB} KB)`);
if (kb > BUDGET_KB) {
  console.error(`check-bundle: over budget by ${(kb - BUDGET_KB).toFixed(0)} KB`);
  process.exit(1);
}
