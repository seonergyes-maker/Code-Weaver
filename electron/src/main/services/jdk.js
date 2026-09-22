'use strict';

const path = require('node:path');
const fs = require('node:fs');
const os = require('node:os');
const { execFile } = require('node:child_process');
const settings = require('./settings');

const EXE = process.platform === 'win32' ? '.exe' : '';

function toolPath(home, tool) {
  return path.join(home, 'bin', `${tool}${EXE}`);
}

function isJdkHome(dir) {
  try {
    return fs.existsSync(toolPath(dir, 'javac')) && fs.existsSync(toolPath(dir, 'java'));
  } catch {
    return false;
  }
}

function run(cmd, args, timeout = 10000) {
  return new Promise((resolve) => {
    execFile(cmd, args, { timeout, windowsHide: true }, (err, stdout, stderr) => {
      resolve({ err, stdout: stdout || '', stderr: stderr || '' });
    });
  });
}

/** javac imprime la versión en stdout o stderr según la versión del JDK. */
async function readVersion(home) {
  const { stdout, stderr } = await run(toolPath(home, 'javac'), ['-version']);
  const text = `${stdout} ${stderr}`.trim();
  const match = text.match(/javac\s+(\d+)(?:\.(\d+))?/i);
  if (!match) return { raw: text, major: 0 };
  let major = parseInt(match[1], 10);
  // JDK 8 se identifica como "javac 1.8.0_x"
  if (major === 1 && match[2]) major = parseInt(match[2], 10);
  return { raw: text.replace(/\s+/g, ' '), major };
}

function candidateRoots() {
  if (process.platform === 'win32') {
    const programFiles = process.env['ProgramFiles'] || 'C:\\Program Files';
    const programFilesX86 = process.env['ProgramFiles(x86)'] || 'C:\\Program Files (x86)';
    const localAppData = process.env['LOCALAPPDATA'] || path.join(os.homedir(), 'AppData', 'Local');
    return [
      path.join(programFiles, 'Java'),
      path.join(programFiles, 'Eclipse Adoptium'),
      path.join(programFiles, 'Amazon Corretto'),
      path.join(programFiles, 'Microsoft'),
      path.join(programFiles, 'Zulu'),
      path.join(programFiles, 'BellSoft'),
      path.join(programFiles, 'RedHat'),
      path.join(programFiles, 'JetBrains'),
      path.join(programFilesX86, 'Java'),
      path.join(localAppData, 'Programs', 'Eclipse Adoptium'),
      path.join(localAppData, 'Programs', 'Java'),
      'C:\\Java'
    ];
  }
  return ['/usr/lib/jvm', '/usr/java', '/opt/java', '/Library/Java/JavaVirtualMachines'];
}

function scanRoots() {
  const found = new Set();
  for (const root of candidateRoots()) {
    let entries = [];
    try {
      if (!fs.existsSync(root)) continue;
      entries = fs.readdirSync(root, { withFileTypes: true });
    } catch { continue; }
    for (const entry of entries) {
      if (!entry.isDirectory()) continue;
      const dir = path.join(root, entry.name);
      if (isJdkHome(dir)) { found.add(dir); continue; }
      // macOS: <dir>/Contents/Home
      const macHome = path.join(dir, 'Contents', 'Home');
      if (isJdkHome(macHome)) found.add(macHome);
    }
  }
  return [...found];
}

/** Resuelve el javac que esté en el PATH y devuelve su JDK home. */
async function fromPath() {
  const finder = process.platform === 'win32' ? 'where' : 'which';
  const { err, stdout } = await run(finder, ['javac']);
  if (err) return null;
  const first = stdout.split(/\r?\n/).map((s) => s.trim()).filter(Boolean)[0];
  if (!first) return null;
  try {
    const real = fs.realpathSync(first);
    const home = path.dirname(path.dirname(real));
    return isJdkHome(home) ? home : null;
  } catch {
    return null;
  }
}

let cache = null;

async function detectAll() {
  const homes = new Set(scanRoots());

  const javaHome = process.env.JAVA_HOME;
  if (javaHome && isJdkHome(javaHome)) homes.add(path.normalize(javaHome));

  const pathHome = await fromPath();
  if (pathHome) homes.add(path.normalize(pathHome));

  const list = [];
  for (const home of homes) {
    const version = await readVersion(home);
    list.push({
      home,
      javac: toolPath(home, 'javac'),
      java: toolPath(home, 'java'),
      jar: toolPath(home, 'jar'),
      version: version.raw,
      major: version.major
    });
  }
  list.sort((a, b) => b.major - a.major || a.home.localeCompare(b.home));
  cache = list;
  return list;
}

/**
 * Devuelve el JDK activo. Si el usuario ha fijado uno en preferencias se usa ese;
 * si no, el de mayor versión encontrado. javac y java salen siempre del MISMO
 * JDK, lo que evita el clásico "class file has wrong version" cuando en el PATH
 * hay un JRE antiguo junto a un JDK moderno.
 */
async function active() {
  const configured = settings.get('jdkHome');
  if (configured && isJdkHome(configured)) {
    const version = await readVersion(configured);
    return {
      home: configured,
      javac: toolPath(configured, 'javac'),
      java: toolPath(configured, 'java'),
      jar: toolPath(configured, 'jar'),
      version: version.raw,
      major: version.major,
      source: 'preferencias'
    };
  }
  const list = cache && cache.length ? cache : await detectAll();
  if (!list.length) return null;
  return { ...list[0], source: 'detección automática' };
}

function invalidate() { cache = null; }

module.exports = { detectAll, active, isJdkHome, invalidate, readVersion };
