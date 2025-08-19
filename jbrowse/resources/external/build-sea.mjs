// Build SEA for all platforms (win/linux/macos) from any host.
// - Creates sea-prep.blob once from resources/external/jbrowse.js
// - Downloads official Node runtimes for the same Node version as process.execPath
// - Injects the blob into each runtime with postject
// - Writes outputs to resources/external/jb-cli:
//     cli-win.exe, cli-linux, cli-macos
// - Cleans up temp files and resources/external/jbrowse.js at the end

import { execFileSync, execSync, spawnSync } from 'node:child_process';
import {
  copyFileSync, chmodSync, existsSync, mkdirSync, rmSync, renameSync, writeFileSync
} from 'node:fs';
import { basename, join, resolve } from 'node:path';
import https from 'node:https';
import { createWriteStream } from 'node:fs';

const ROOT = resolve('.');
const OUTDIR = join(ROOT, 'resources', 'external', 'jb-cli');
const TMPDIR = join(ROOT, '.sea-tmp');
const INPUT_JS = join(ROOT, 'resources', 'external', 'jbrowse.js');

const NODE_VERSION = process.versions.node;
const DIST_BASE = process.env.NODE_DIST_URL || 'https://nodejs.org/dist';
const TARGETS = [
  ['win-x64',   'cli-win.exe', 'zip'],
  ['linux-x64', 'cli-linux',   'tar.xz'],
  ['darwin-x64','cli-macos',   'tar.xz'],
  // ['darwin-arm64','cli-macos-arm64','tar.xz'],
  // ['linux-arm64','cli-linux-arm64','tar.xz'],
];

const SENTINEL = 'NODE_SEA_FUSE_fce680ab2cc467b6e072b8b5df1996b2';
const POSTJECT_PKG = 'postject@1.0.0-alpha.6';

function log(s){ console.log(s); }
function warn(s){ console.warn(s); }
function fail(e){ console.error(e); process.exit(1); }

function ensureDirs(){
  mkdirSync(OUTDIR, { recursive: true });
  mkdirSync(TMPDIR, { recursive: true });
}

function httpDownload(url, dest){
  return new Promise((resolveP, rejectP)=>{
    const file = createWriteStream(dest);
    https.get(url, res=>{
      if(res.statusCode && res.statusCode >= 300 && res.statusCode < 400 && res.headers.location){
        httpDownload(res.headers.location, dest).then(resolveP, rejectP);
        return;
      }
      if(res.statusCode !== 200){
        rejectP(new Error(`Download failed ${res.statusCode}: ${url}`));
        return;
      }
      res.pipe(file);
      file.on('finish', ()=>file.close(()=>resolveP(void 0)));
    }).on('error', err=>{ rejectP(err); });
  });
}

function runOrThrow(cmd, args, opts={}){
  execFileSync(cmd, args, { stdio: 'inherit', ...opts });
}

function shellOrThrow(command){
  execSync(command, { stdio: 'inherit', shell: true });
}

// postject runner with fallbacks
function runPostject(args){
  const isWin = process.platform === 'win32';
  const npxCmd = isWin ? 'npx.cmd' : 'npx';
  const npmCmd = isWin ? 'npm.cmd' : 'npm';
  try { runOrThrow(npxCmd, ['--yes', POSTJECT_PKG, ...args]); return; }
  catch{ warn('[postject] npx failed, trying npm exec…'); }
  try { runOrThrow(npmCmd, ['exec', '-y', POSTJECT_PKG, '--', ...args]); return; }
  catch{ warn('[postject] npm exec failed, trying shell npx…'); }
  try { shellOrThrow(`npx --yes ${POSTJECT_PKG} ${args.map(a=>`"${a}"`).join(' ')}`); return; }
  catch{ warn('[postject] shell npx failed, trying shell npm exec…'); }
  shellOrThrow(`npm exec -y ${POSTJECT_PKG} -- ${args.map(a=>`"${a}"`).join(' ')}`);
}

function extractZip(zipPath, outDir){
  if (process.platform === 'win32') {
    shellOrThrow(`powershell -NoProfile -Command "Expand-Archive -Force '${zipPath.replace(/'/g, "''")}' '${outDir.replace(/'/g, "''")}'"`);
  } else {
    shellOrThrow(`unzip -o "${zipPath}" -d "${outDir}"`);
  }
}

// extract only bin/node from tar.xz to avoid symlink issues on Windows
function extractNodeFromTarXz(tarxzPath, outDir, target) {
  const innerPath = `node-v${NODE_VERSION}-${target}/bin/node`;
  try { shellOrThrow(`tar -xJf "${tarxzPath}" -C "${outDir}" "${innerPath}"`); }
  catch { shellOrThrow(`bsdtar -xf "${tarxzPath}" -C "${outDir}" "${innerPath}"`); }
  return join(outDir, innerPath);
}

function nodeBinaryPathFromExtract(dir, target){
  const base = `node-v${NODE_VERSION}-${target}`;
  if (target.startsWith('win')) return join(dir, base, 'node.exe');
  return join(dir, base, 'bin', 'node');
}

function injectForTarget(target, outName, archiveExt, blobPath){
  const distUrl = `${DIST_BASE}/v${NODE_VERSION}/node-v${NODE_VERSION}-${target}.${archiveExt}`;
  const dlPath = join(TMPDIR, `node-${target}.${archiveExt}`);

  log(`\n=== Target ${target} ===`);
  log(`Downloading: ${distUrl}`);
  httpDownload(distUrl, dlPath).then(()=>{
    log('Extracting…');
    let nodePath;
    if (archiveExt === 'zip') {
      extractZip(dlPath, TMPDIR);
      nodePath = nodeBinaryPathFromExtract(TMPDIR, target);
    } else {
      nodePath = extractNodeFromTarXz(dlPath, TMPDIR, target);
    }
    if (!existsSync(nodePath)) fail(`node binary not found in ${nodePath}`);

    const workExe = join(TMPDIR, `work-${outName}`);
    copyFileSync(nodePath, workExe);

    const postjectArgs = target.startsWith('darwin')
      ? [workExe, 'NODE_SEA_BLOB', blobPath, '--sentinel-fuse', SENTINEL, '--macho-segment-name', 'NODE_SEA']
      : [workExe, 'NODE_SEA_BLOB', blobPath, '--sentinel-fuse', SENTINEL];

    log('Injecting SEA blob…');
    runPostject(postjectArgs);

    if (!target.startsWith('win')) chmodSync(workExe, 0o755);

    const finalPath = join(OUTDIR, outName);
    if (existsSync(finalPath)) rmSync(finalPath, { force: true });
    renameSync(workExe, finalPath);

    rmSync(dlPath, { force: true });
    try { rmSync(join(TMPDIR, `node-v${NODE_VERSION}-${target}`), { recursive: true, force: true }); } catch {}
    log(`Wrote ${finalPath}`);
  }).catch(fail);
}

function buildBlobOnce(){
  const cfgPath = join(TMPDIR, 'sea-config.json');
  const blobPath = join(TMPDIR, 'sea-prep.blob');
  log('Creating SEA blob…');
  const cfg = { main: INPUT_JS, output: blobPath, disableExperimentalSEAWarning: true };
  writeFileSync(cfgPath, JSON.stringify(cfg, null, 2));
  runOrThrow(process.execPath, ['--experimental-sea-config', cfgPath]);
  return blobPath;
}

async function main(){
  ensureDirs();
  if (!existsSync(INPUT_JS)) fail(`Missing ${INPUT_JS}. Run your fetch step first.`);
  rmSync(TMPDIR, { recursive: true, force: true });
  mkdirSync(TMPDIR, { recursive: true });

  const blob = buildBlobOnce();

  for (const [target, outName, ext] of TARGETS) {
    await new Promise((resolveP, rejectP)=>{
      try {
        injectForTarget(target, outName, ext, blob);
        const interval = setInterval(()=>{
          if (existsSync(join(OUTDIR, outName))) {
            clearInterval(interval);
            resolveP();
          }
        }, 500);
      } catch (e) {
        rejectP(e);
      }
    });
  }

  try { rmSync(TMPDIR, { recursive: true, force: true }); } catch {}
  try { if (existsSync(INPUT_JS)) rmSync(INPUT_JS, { force: true }); } catch {}

  log('\nAll targets built.');
}

main().catch(fail);
