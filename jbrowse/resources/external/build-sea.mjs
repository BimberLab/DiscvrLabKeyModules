import { execFileSync, execSync, spawnSync } from 'node:child_process';
import { copyFileSync, chmodSync, existsSync, mkdirSync, rmSync, renameSync, writeFileSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';

const input = process.argv[2];
if (!input) {
  console.error('Usage: node build-sea.mjs <input.js> [outputBaseName]');
  process.exit(1);
}
const inputAbs = resolve(input);
const baseName = process.argv[3] || basename(input, '.js');

const ROOT = resolve('.');
const OUTDIR = join(ROOT, 'resources', 'external', 'jb-cli');
mkdirSync(OUTDIR, { recursive: true });

const platform = process.platform; // 'win32' | 'darwin' | 'linux'
const outName =
  platform === 'win32' ? 'cli-win.exe' :
  platform === 'darwin' ? 'cli-macos' : 'cli-linux';

const tmpCfg = 'sea-config.json';
const tmpBlob = 'sea-prep.blob';
const tmpOut = `${baseName}${platform === 'win32' ? '.exe' : ''}`;

console.log(`SEA build: ${inputAbs} -> ${join(OUTDIR, outName)} [${platform}]`);

// 1) Create SEA config
const cfg = {
  main: inputAbs,
  output: tmpBlob,
  disableExperimentalSEAWarning: true,
};
writeFileSync(tmpCfg, JSON.stringify(cfg, null, 2));

// 2) Produce blob
execFileSync(process.execPath, ['--experimental-sea-config', tmpCfg], { stdio: 'inherit' });

// 3) Copy current Node runtime as the base executable
copyFileSync(process.execPath, tmpOut);

// 4) Remove signature (it'll be invalid once we postject the Jbrowse CLI into the node runtime executable)
if (platform === 'darwin') {
  try { spawnSync('codesign', ['--remove-signature', tmpOut], { stdio: 'inherit' }); } catch {}
}
if (platform === 'win32') {
  try { spawnSync('signtool', ['remove', '/s', tmpOut], { stdio: 'inherit' }); } catch {}
}

// Helper: run postject with multiple fallbacks
function runPostject(argsList) {
  const isWin = platform === 'win32';
  const npxCmd = isWin ? 'npx.cmd' : 'npx';
  const npmCmd = isWin ? 'npm.cmd' : 'npm';
  const postjectPkg = 'postject@1.0.0-alpha.6';

  // Attempt 1: npx (execFileSync)
  try {
    execFileSync(npxCmd, ['--yes', postjectPkg, ...argsList], { stdio: 'inherit' });
    return;
  } catch (e1) {
    console.warn('[postject] npx (execFileSync) failed, trying npm exec…');
    // Attempt 2: npm exec (execFileSync)
    try {
      execFileSync(npmCmd, ['exec', '-y', postjectPkg, '--', ...argsList], { stdio: 'inherit' });
      return;
    } catch (e2) {
      console.warn('[postject] npm exec (execFileSync) failed, trying shell npx…');
      // Attempt 3: npx via shell (execSync)
      try {
        const cmd = `npx --yes ${postjectPkg} ${argsList.map(a => `"${a}"`).join(' ')}`;
        execSync(cmd, { stdio: 'inherit', shell: true });
        return;
      } catch (e3) {
        console.warn('[postject] shell npx failed, trying shell npm exec…');
        // Attempt 4: npm exec via shell (execSync)
        const cmd2 = `npm exec -y ${postjectPkg} -- ${argsList.map(a => `"${a}"`).join(' ')}`;
        execSync(cmd2, { stdio: 'inherit', shell: true });
      }
    }
  }
}

// Postject magic from the docs to do the jbrowse->node appending
const SENTINEL = 'NODE_SEA_FUSE_fce680ab2cc467b6e072b8b5df1996b2';
const postjectArgs = platform === 'darwin'
  ? [tmpOut, 'NODE_SEA_BLOB', tmpBlob, '--sentinel-fuse', SENTINEL, '--macho-segment-name', 'NODE_SEA']
  : [tmpOut, 'NODE_SEA_BLOB', tmpBlob, '--sentinel-fuse', SENTINEL];

// 5) Inject the SEA blob
runPostject(postjectArgs);

// 6) Re-sign for the new combined executable
if (platform === 'darwin') {
  try { spawnSync('codesign', ['--sign', '-', tmpOut], { stdio: 'inherit' }); } catch {}
}
if (platform === 'win32') {
  try { spawnSync('signtool', ['sign', '/fd', 'SHA256', tmpOut], { stdio: 'inherit' }); } catch {}
}

// 7) POSIX chmod
if (platform !== 'win32') {
  chmodSync(tmpOut, 0o755);
}

// 8) Move to final destination
const finalPath = join(OUTDIR, outName);
if (existsSync(finalPath)) rmSync(finalPath, { force: true });
renameSync(tmpOut, finalPath);

// 9) Cleanup
rmSync(tmpCfg, { force: true });
rmSync(tmpBlob, { force: true });

const jbrowseJsPath = join(ROOT, 'resources', 'external', 'jbrowse.js');
if (existsSync(jbrowseJsPath)) {
  rmSync(jbrowseJsPath, { force: true });
}

console.log(`Done: ${finalPath}`);