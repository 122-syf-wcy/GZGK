#!/usr/bin/env node
/**
 * Build-time regulatory compliance gate for gzgk-web.
 * Reads the shared single source of truth `contracts/compliance-terms.json` and scans
 * all Vue/TS source files to prevent banned guarantee wording from ever reaching production UI.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(__dirname, '..');
const contractsPath = path.resolve(webRoot, '../../contracts/compliance-terms.json');
const srcDir = path.resolve(webRoot, 'src');

const terms = JSON.parse(fs.readFileSync(contractsPath, 'utf-8'));
const hardTerms = terms.hard || [];

function walk(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(full));
    else if (/\.(vue|ts|tsx|js)$/.test(entry.name)) out.push(full);
  }
  return out;
}

const violations = [];
for (const file of walk(srcDir)) {
  const content = fs.readFileSync(file, 'utf-8');
  for (const term of hardTerms) {
    if (content.includes(term)) {
      violations.push({ file: path.relative(webRoot, file), term });
    }
  }
}

if (violations.length > 0) {
  console.error('[compliance] FAILED: Found forbidden regulatory terms in gzgk-web/src:');
  for (const v of violations) {
    console.error(`  - ${v.file}: "${v.term}"`);
  }
  process.exit(1);
}

console.log(`[compliance] PASSED: Scanned gzgk-web/src against ${hardTerms.length} hard regulatory terms (0 violations).`);
