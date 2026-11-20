import { readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
const dir = new URL('../app/src/test/js/', import.meta.url);
const tests = readdirSync(dir).filter(name => name.endsWith('.test.cjs')).sort();
if (!tests.length) throw new Error('No JavaScript test files found.');
for (const name of tests) {
  const result = spawnSync(process.execPath, [fileURLToPath(new URL(name, dir))], { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}
console.log(`Passed ${tests.length} JavaScript test suites.`);
