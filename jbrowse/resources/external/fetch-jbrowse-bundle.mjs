import { execSync } from 'node:child_process';
import { existsSync, mkdirSync, copyFileSync, rmSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve('.');
const BUILD = join(ROOT, 'buildCli');
const OUTDIR = join(ROOT, 'resources', 'external');
const OUTFILE = join(OUTDIR, 'jbrowse.js');

async function extractTgz(tgzPath, cwd) {
  try {
    const mod = await import('tar').catch(() => null);
    const tar = mod?.default ?? mod;
    if (tar?.x) {
      await tar.x({ file: tgzPath, cwd });
      return;
    }
  } catch {
  }
  execSync(`tar -xzf "${tgzPath}" -C "${cwd}"`, { stdio: 'inherit', shell: true });
}

async function main() {
  if (existsSync(BUILD)) rmSync(BUILD, { recursive: true, force: true });
  mkdirSync(BUILD, { recursive: true });
  mkdirSync(OUTDIR, { recursive: true });

  console.log('Packing @jbrowse/cli (latest)…');
  const out = execSync('npm pack @jbrowse/cli', {
    cwd: BUILD,
    stdio: ['ignore', 'pipe', 'inherit'],
    shell: true,
  })
    .toString()
    .trim();

  const tgz = join(BUILD, out);
  console.log(`Downloaded: ${tgz}`);

  console.log('Extracting tarball…');
  await extractTgz(tgz, BUILD);

  const bundled = join(BUILD, 'package', 'bundle', 'index.js');
  if (!existsSync(bundled)) {
    throw new Error(`bundle/index.js not found at ${bundled}`);
  }

  copyFileSync(bundled, OUTFILE);
  console.log(`Copied bundle to ${OUTFILE}`);

  rmSync(BUILD, { recursive: true, force: true });
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
