import { execSync } from 'node:child_process';
import { existsSync, mkdirSync, copyFileSync, rmSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve('.');
const BUILD = join(ROOT, 'buildCli');
const OUTDIR = join(ROOT, 'resources', 'external');
const OUTFILE = join(OUTDIR, 'jbrowse.js');

function getResolvedCoreVersion() {
  // Prefer lockfile, fallback to node_modules
  const lockPath = join(ROOT, 'package-lock.json');
  if (existsSync(lockPath)) {
    const lock = JSON.parse(readFileSync(lockPath, 'utf8'));
    const v =
      lock?.packages?.['node_modules/@jbrowse/core']?.version ||
      lock?.dependencies?.['@jbrowse/core']?.version;
    if (v) return v;
  }

  const corePkg = join(ROOT, 'node_modules', '@jbrowse', 'core', 'package.json');
  if (existsSync(corePkg)) {
    return JSON.parse(readFileSync(corePkg, 'utf8')).version;
  }

  throw new Error('Could not determine resolved @jbrowse/core version (no lockfile entry and no node_modules).');
}

function semverLt(a, b) {
  const pa = String(a).match(/(\d+)\.(\d+)\.(\d+)/);
  const pb = String(b).match(/(\d+)\.(\d+)\.(\d+)/);
  if (!pa || !pb) return false;
  for (let i = 1; i <= 3; i++) {
    const da = Number(pa[i]);
    const db = Number(pb[i]);
    if (da !== db) return da < db;
  }
  return false;
}

function floorVersion(spec, min) {
  const m = String(spec).match(/^(\^|~|>=)?\s*(\d+\.\d+\.\d+)(.*)$/);
  if (!m) return spec;
  const [, op = '', v, rest = ''] = m;
  return semverLt(v, min) ? `${op}${min}${rest}` : spec;
}

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

function findCliEntrypoint(buildDir) {
  const candidates = [
    join(buildDir, 'package', 'bundle', 'index.js'),
    join(buildDir, 'package', 'lib', 'index.js')
  ];

  for (const p of candidates) {
    if (existsSync(p)) return p;
  }

  throw new Error(
    `Could not find @jbrowse/cli entrypoint. Tried:\n` +
    candidates.map(c => `  - ${c}`).join('\n')
  );
}

async function main() {
  if (existsSync(BUILD)) rmSync(BUILD, { recursive: true, force: true });
  mkdirSync(BUILD, { recursive: true });
  mkdirSync(OUTDIR, { recursive: true });

  const coreVersion = getResolvedCoreVersion();
  const cliSpec = floorVersion(process.env.JBROWSE_CLI_VERSION || coreVersion, '3.6.0');

  console.log(`Packing @jbrowse/cli@${cliSpec} (core resolved: ${coreVersion})…`);
  const out = execSync(`npm pack @jbrowse/cli@${cliSpec}`, {
    cwd: BUILD,
    stdio: ['ignore', 'pipe', 'inherit'],
    shell: true,
  }).toString().trim();

  const tgz = join(BUILD, out);
  console.log(`Downloaded: ${tgz}`);

  console.log('Extracting tarball…');
  await extractTgz(tgz, BUILD);

  const entry = findCliEntrypoint(BUILD);

  copyFileSync(entry, OUTFILE);
  console.log(`Copied bundle to ${OUTFILE}`);

  rmSync(BUILD, { recursive: true, force: true });
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
