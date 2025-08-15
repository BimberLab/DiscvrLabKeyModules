import { execSync } from 'node:child_process';
import { existsSync, mkdirSync, copyFileSync, rmSync } from 'node:fs';
import { join, resolve } from 'node:path';
import tar from 'tar';

const ROOT = resolve('.');
const BUILD = join(ROOT, 'buildCli');
const OUTDIR = join(ROOT, 'resources', 'external');
const OUTFILE = join(OUTDIR, 'jbrowse.js');

if (existsSync(BUILD)) rmSync(BUILD, { recursive: true, force: true });
mkdirSync(BUILD, { recursive: true });
mkdirSync(OUTDIR, { recursive: true });

console.log('Packing @jbrowse/cli (latest)…');
const out = execSync('npm pack @jbrowse/cli', { cwd: BUILD, stdio: ['ignore', 'pipe', 'inherit'] }).toString().trim();

// npm outputs the filename on stdout, e.g. "jbrowse-cli-3.6.4.tgz"
const tgz = join(BUILD, out);
console.log(`Downloaded: ${tgz}`);

console.log('Extracting tarball…');
await tar.x({ file: tgz, cwd: BUILD });

const bundled = join(BUILD, 'package', 'bundle', 'index.js');
if (!existsSync(bundled)) {
  throw new Error(`bundle/index.js not found at ${bundled}`);
}

copyFileSync(bundled, OUTFILE);

rmSync(BUILD, { recursive: true, force: true });

console.log(`Copied bundle to ${OUTFILE}`);