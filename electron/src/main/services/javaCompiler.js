'use strict';

const path = require('node:path');
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const os = require('node:os');
const { spawn } = require('node:child_process');
const AdmZip = require('adm-zip');

const jdk = require('./jdk');
const deps = require('./dependencies');
const settings = require('./settings');
const workspace = require('./workspace');

// ---------------------------------------------------------------- utilidades

function normalizeName(name) {
  const base = path.basename(String(name || '').trim());
  return base.endsWith('.java') ? base : `${base}.java`;
}

function findClassName(code) {
  const pub = code.match(/public\s+(?:final\s+|abstract\s+)?(?:class|interface|enum|record)\s+(\w+)/);
  if (pub) return pub[1];
  const any = code.match(/(?:class|interface|enum|record)\s+(\w+)/);
  return any ? any[1] : 'Main';
}

function packageOf(code) {
  const match = code.match(/^\s*package\s+([\w.]+)\s*;/m);
  return match ? match[1] : '';
}

/** Todas las clases del proyecto que tienen un main ejecutable. */
function findMainClasses(files) {
  const result = [];
  for (const [name, code] of Object.entries(files || {})) {
    if (!/public\s+static\s+void\s+main\s*\(/.test(code)) continue;
    const cls = findClassName(code);
    const pkg = packageOf(code);
    result.push({ file: normalizeName(name), className: pkg ? `${pkg}.${cls}` : cls });
  }
  return result;
}

function pickMainClass(files, preferred) {
  const candidates = findMainClasses(files);
  if (!candidates.length) return null;
  if (preferred) {
    const match = candidates.find((c) => c.className === preferred || c.file === normalizeName(preferred));
    if (match) return match.className;
  }
  const main = candidates.find((c) => /(^|\.)Main$/.test(c.className));
  return (main || candidates[0]).className;
}

/** Escribe los archivos en disco respetando la ruta del package. */
async function materialize(files, dir) {
  const written = [];
  for (const [rawName, code] of Object.entries(files || {})) {
    const name = normalizeName(rawName);
    const pkg = packageOf(code);
    const targetDir = pkg ? path.join(dir, ...pkg.split('.')) : dir;
    await fsp.mkdir(targetDir, { recursive: true });
    const full = path.join(targetDir, name);
    await fsp.writeFile(full, code, 'utf8');
    written.push(full);
  }
  return written;
}

async function makeTempDir(prefix) {
  return fsp.mkdtemp(path.join(os.tmpdir(), prefix));
}

async function cleanup(dir) {
  try { await fsp.rm(dir, { recursive: true, force: true }); } catch { /* sin importancia */ }
}

/**
 * Convierte la salida de javac en diagnósticos estructurados para poder
 * marcarlos en el editor.
 */
function parseDiagnostics(stderr, dir) {
  const diagnostics = [];
  const lines = stderr.split(/\r?\n/);
  for (let i = 0; i < lines.length; i += 1) {
    const match = lines[i].match(/^(.*\.java):(\d+):\s*(error|warning|advertencia|aviso):\s*(.*)$/i);
    if (!match) continue;
    const [, filePath, lineNo, severity, message] = match;
    // La columna se deduce del cursor '^' que javac imprime dos líneas después
    let column = 1;
    const caret = lines[i + 2];
    if (caret && caret.includes('^')) column = caret.indexOf('^') + 1;
    diagnostics.push({
      file: path.basename(filePath),
      relative: dir ? path.relative(dir, filePath) : filePath,
      line: parseInt(lineNo, 10),
      column,
      severity: /error/i.test(severity) ? 'error' : 'warning',
      message: message.trim()
    });
  }
  return diagnostics;
}

function runProcess(cmd, args, options = {}) {
  return new Promise((resolve) => {
    let stdout = '';
    let stderr = '';
    let child;
    try {
      child = spawn(cmd, args, { windowsHide: true, ...options });
    } catch (err) {
      resolve({ code: -1, stdout: '', stderr: err.message });
      return;
    }
    const timeoutMs = options.timeoutMs || 0;
    let timer = null;
    if (timeoutMs > 0) {
      timer = setTimeout(() => { try { child.kill(); } catch { /* ya terminado */ } }, timeoutMs);
    }
    child.stdout.setEncoding('utf8');
    child.stderr.setEncoding('utf8');
    child.stdout.on('data', (d) => { stdout += d; });
    child.stderr.on('data', (d) => { stderr += d; });
    child.on('error', (err) => {
      if (timer) clearTimeout(timer);
      resolve({ code: -1, stdout, stderr: `${stderr}\n${err.message}` });
    });
    child.on('close', (code) => {
      if (timer) clearTimeout(timer);
      resolve({ code, stdout, stderr });
    });
  });
}

function encodingFlags(major, prefix = '') {
  const flags = [`${prefix}-Dfile.encoding=UTF-8`];
  if (major >= 19) {
    flags.push(`${prefix}-Dstdout.encoding=UTF-8`, `${prefix}-Dstderr.encoding=UTF-8`);
  }
  return flags;
}

async function requireJdk() {
  const active = await jdk.active();
  if (!active) {
    throw new Error(
      'No se ha encontrado ningún JDK. Instala un JDK (por ejemplo Eclipse Temurin 21) ' +
      'o indica la ruta en Preferencias.'
    );
  }
  return active;
}

// ---------------------------------------------------------------- compilación

async function compileTo(files, classesDir, active) {
  const sourceDir = path.join(classesDir, '..', 'src');
  await fsp.mkdir(sourceDir, { recursive: true });
  await fsp.mkdir(classesDir, { recursive: true });
  const sources = await materialize(files, sourceDir);
  if (!sources.length) {
    return { success: false, error: 'No hay archivos .java que compilar', diagnostics: [] };
  }

  const classpath = deps.classpath();
  const target = String(settings.get('javaTarget') || '21');
  const args = ['-encoding', 'UTF-8', '-d', classesDir, ...encodingFlags(active.major, '-J')];

  // --release solo está disponible desde JDK 9 y no admite versiones superiores al propio compilador
  const releaseNum = parseInt(target, 10);
  if (active.major >= 9 && releaseNum && releaseNum <= active.major) {
    args.push('--release', String(releaseNum));
  }
  if (classpath) args.push('-cp', classpath);
  args.push(...sources);

  const result = await runProcess(active.javac, args, { timeoutMs: 120000 });
  const output = `${result.stdout}${result.stderr}`;
  const diagnostics = parseDiagnostics(output, sourceDir);

  if (result.code !== 0) {
    return { success: false, error: output.trim() || 'javac terminó con error', diagnostics, sourceDir };
  }
  return { success: true, warnings: output.trim(), diagnostics, sourceDir };
}

async function compile(files) {
  const active = await requireJdk();
  const work = await makeTempDir('cw-compile-');
  try {
    const classesDir = path.join(work, 'classes');
    const result = await compileTo(files, classesDir, active);
    if (!result.success) {
      return { success: false, error: result.error, diagnostics: result.diagnostics, jdk: active.version };
    }
    const classes = [];
    const walk = (dir) => {
      for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) walk(full);
        else if (entry.name.endsWith('.class')) classes.push(path.relative(classesDir, full));
      }
    };
    walk(classesDir);
    return {
      success: true,
      message: `Compilación correcta: ${classes.length} archivo(s) .class`,
      classCount: classes.length,
      warnings: result.warnings,
      diagnostics: result.diagnostics,
      jdk: active.version
    };
  } finally {
    await cleanup(work);
  }
}

// ---------------------------------------------------------------- ejecución

let current = null; // { child, workDir }

function isRunning() {
  return Boolean(current && current.child && current.child.exitCode === null);
}

/**
 * Compila y ejecuta el proyecto. La salida se envía en streaming a través
 * de onOutput para que la consola sea interactiva y no espere al final.
 */
async function run(files, preferredMain, args, { onOutput, onExit }) {
  if (isRunning()) {
    return { success: false, error: 'Ya hay una ejecución en curso. Detenla antes de lanzar otra.' };
  }
  const active = await requireJdk();
  const mainClass = pickMainClass(files, preferredMain);
  if (!mainClass) {
    return {
      success: false,
      error: 'No se ha encontrado ningún método main. Añade "public static void main(String[] args)" a una clase.'
    };
  }

  const work = await makeTempDir('cw-run-');
  const classesDir = path.join(work, 'classes');
  onOutput?.({ stream: 'system', text: `> Compilando con ${active.version} (${active.home})\n` });

  const compiled = await compileTo(files, classesDir, active);
  if (!compiled.success) {
    await cleanup(work);
    return { success: false, error: compiled.error, diagnostics: compiled.diagnostics, mainClass };
  }
  if (compiled.warnings) {
    onOutput?.({ stream: 'stderr', text: `${compiled.warnings}\n` });
  }

  const classpath = [classesDir, ...deps.jarFiles()].join(path.delimiter);
  const javaArgs = [
    ...encodingFlags(active.major),
    `-Djava.library.path=${workspace.libsDir()}`,
    '-cp', classpath,
    mainClass,
    ...(Array.isArray(args) ? args : [])
  ];

  onOutput?.({ stream: 'system', text: `> java ${mainClass}\n\n` });

  const child = spawn(active.java, javaArgs, { windowsHide: true, cwd: workspace.outputDir() });
  current = { child, workDir: work };

  child.stdout.setEncoding('utf8');
  child.stderr.setEncoding('utf8');
  child.stdout.on('data', (text) => onOutput?.({ stream: 'stdout', text }));
  child.stderr.on('data', (text) => onOutput?.({ stream: 'stderr', text }));

  child.on('error', (err) => {
    onOutput?.({ stream: 'system', text: `\n> No se pudo lanzar java: ${err.message}\n` });
  });

  child.on('close', async (code, signal) => {
    current = null;
    await cleanup(work);
    onExit?.({ code, signal, mainClass });
  });

  const timeoutSec = Number(settings.get('runTimeoutSec') || 0);
  if (timeoutSec > 0) {
    setTimeout(() => {
      if (isRunning()) {
        onOutput?.({ stream: 'system', text: `\n> Tiempo máximo (${timeoutSec}s) alcanzado, deteniendo.\n` });
        stop();
      }
    }, timeoutSec * 1000);
  }

  return { success: true, mainClass, pid: child.pid, jdk: active.version };
}

function writeStdin(text) {
  if (!isRunning()) return { success: false, error: 'No hay ningún proceso en ejecución' };
  try {
    current.child.stdin.write(`${text}\n`);
    return { success: true };
  } catch (err) {
    return { success: false, error: err.message };
  }
}

function stop() {
  if (!isRunning()) return { success: false, error: 'No hay ninguna ejecución activa' };
  const { child } = current;
  try {
    if (process.platform === 'win32') {
      spawn('taskkill', ['/pid', String(child.pid), '/f', '/t'], { windowsHide: true });
    } else {
      child.kill('SIGTERM');
      setTimeout(() => { try { child.kill('SIGKILL'); } catch { /* ya terminado */ } }, 2000);
    }
    return { success: true };
  } catch (err) {
    return { success: false, error: err.message };
  }
}

// ---------------------------------------------------------------- empaquetado

function addDirToZip(zip, dir, base) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      addDirToZip(zip, full, base);
    } else {
      const rel = path.relative(base, full).split(path.sep).join('/');
      zip.addFile(rel, fs.readFileSync(full));
    }
  }
}

/**
 * Crea el JAR. Con fat=true incorpora también el contenido de todas las
 * librerías de la carpeta libs, de modo que el .jar resultante se ejecuta
 * con un simple "java -jar" sin classpath adicional.
 */
async function buildJar(files, jarName, preferredMain, fat = true) {
  const active = await requireJdk();
  const mainClass = pickMainClass(files, preferredMain);
  if (!mainClass) {
    return { success: false, error: 'No se ha encontrado ningún método main para el JAR ejecutable.' };
  }

  let name = String(jarName || `${mainClass.split('.').pop()}.jar`).trim();
  if (!name.toLowerCase().endsWith('.jar')) name += '.jar';
  name = path.basename(name);

  const work = await makeTempDir('cw-jar-');
  try {
    const classesDir = path.join(work, 'classes');
    const compiled = await compileTo(files, classesDir, active);
    if (!compiled.success) {
      return { success: false, error: compiled.error, diagnostics: compiled.diagnostics, mainClass };
    }

    const zip = new AdmZip();
    const manifest =
      'Manifest-Version: 1.0\r\n' +
      `Main-Class: ${mainClass}\r\n` +
      'Created-By: Code Weaver (Daemon4)\r\n\r\n';
    zip.addFile('META-INF/MANIFEST.MF', Buffer.from(manifest, 'utf8'));

    const seen = new Set(['META-INF/MANIFEST.MF']);
    addDirToZip(zip, classesDir, classesDir);
    for (const entry of zip.getEntries()) seen.add(entry.entryName);

    const included = [];
    if (fat) {
      for (const jarPath of deps.jarFiles()) {
        try {
          const dep = new AdmZip(jarPath);
          for (const entry of dep.getEntries()) {
            if (entry.isDirectory) continue;
            const entryName = entry.entryName;
            if (seen.has(entryName)) continue;
            if (entryName.startsWith('META-INF/')) {
              if (/\.(SF|DSA|RSA|EC)$/i.test(entryName)) continue;
              if (entryName === 'META-INF/MANIFEST.MF') continue;
              if (entryName.startsWith('META-INF/versions/')) continue;
            }
            if (/^module-info\.class$/i.test(entryName)) continue;
            zip.addFile(entryName, entry.getData());
            seen.add(entryName);
          }
          included.push(path.basename(jarPath));
        } catch (err) {
          console.error('No se pudo incorporar', jarPath, err.message);
        }
      }
    }

    const outDir = workspace.outputDir();
    await fsp.mkdir(outDir, { recursive: true });
    const jarPath = path.join(outDir, name);
    zip.writeZip(jarPath);

    const stat = await fsp.stat(jarPath);
    return {
      success: true,
      jarPath,
      jarName: name,
      mainClass,
      libsIncluded: included,
      sizeKb: Math.round(stat.size / 1024),
      message: fat
        ? `${name} creado (${Math.round(stat.size / 1024)} KB, ${included.length} librería(s) incluidas)`
        : `${name} creado (${Math.round(stat.size / 1024)} KB)`
    };
  } catch (err) {
    return { success: false, error: err.message };
  } finally {
    await cleanup(work);
  }
}

module.exports = {
  compile, run, stop, writeStdin, isRunning, buildJar,
  findMainClasses, findClassName, pickMainClass, parseDiagnostics, normalizeName
};
