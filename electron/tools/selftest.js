/* Prueba del núcleo de Code Weaver sin abrir ventana: JDK, compilación, ejecución y JAR. */
'use strict';
const path = require('node:path');
const os = require('node:os');
const fs = require('node:fs');
const Module = require('node:module');

const FAKE_USERDATA = path.join(os.tmpdir(), 'cw-selftest-data');
fs.mkdirSync(FAKE_USERDATA, { recursive: true });

const electronStub = {
  app: {
    getPath: (name) => (name === 'userData' ? FAKE_USERDATA : os.tmpdir()),
    getVersion: () => '1.0.0-test'
  },
  safeStorage: { isEncryptionAvailable: () => false },
  shell: {}, dialog: {}, ipcMain: { handle: () => {} }, protocol: {}, net: {}, Menu: {}, BrowserWindow: {}
};

const originalLoad = Module._load;
Module._load = function (request, parent, isMain) {
  if (request === 'electron') return electronStub;
  return originalLoad.apply(this, arguments);
};

const ROOT = path.join(__dirname, '..');
const settings = require(path.join(ROOT, 'src/main/services/settings.js'));
const workspace = require(path.join(ROOT, 'src/main/services/workspace.js'));
const jdk = require(path.join(ROOT, 'src/main/services/jdk.js'));
const compiler = require(path.join(ROOT, 'src/main/services/javaCompiler.js'));
const deps = require(path.join(ROOT, 'src/main/services/dependencies.js'));
const templates = require(path.join(ROOT, 'src/main/services/templates.js'));
const projects = require(path.join(ROOT, 'src/main/services/projects.js'));

const FILES = {
  'Main.java': [
    'import java.util.List;',
    'public class Main {',
    '    public static void main(String[] args) {',
    '        Saludo s = new Saludo("Code Weaver");',
    '        System.out.println(s.texto());',
    '        System.out.println("acentos: ñ á é í ó ú");',
    '        System.out.println("java: " + System.getProperty("java.version"));',
    '    }',
    '}'
  ].join('\n'),
  'Saludo.java': [
    'public class Saludo {',
    '    private final String nombre;',
    '    public Saludo(String nombre) { this.nombre = nombre; }',
    '    public String texto() { return "Hola desde " + nombre; }',
    '}'
  ].join('\n')
};

function line(label, ok, extra = '') {
  console.log(`${ok ? '[OK]   ' : '[FALLO]'} ${label}${extra ? ' :: ' + extra : ''}`);
  return ok;
}

(async () => {
  settings.init();
  await workspace.init(ROOT);
  let allOk = true;

  const jdks = await jdk.detectAll();
  allOk &= line(`JDK detectados: ${jdks.length}`, jdks.length > 0,
    jdks.map((j) => `${j.major}@${j.home}`).join(' | '));
  const active = await jdk.active();
  allOk &= line('JDK activo', Boolean(active), active ? `${active.version} (${active.home})` : 'ninguno');

  const libs = deps.listInstalled();
  allOk &= line(`Librerías en la carpeta de datos: ${libs.length}`, libs.length > 0,
    libs.map((l) => l.name).join(', '));

  const tpl = templates.list();
  allOk &= line(`Plantillas: ${tpl.length}`, tpl.length >= 8, tpl.map((t) => t.id).join(', '));

  const rfid = await templates.load('rfid-zebra-fx7500');
  allOk &= line('Plantilla RFID cargable', rfid.success, rfid.success ? `${Object.keys(rfid.files).length} archivos` : rfid.error);

  const mains = compiler.findMainClasses(FILES);
  allOk &= line('Detección de main', mains.length === 1 && mains[0].className === 'Main', JSON.stringify(mains));

  const compiled = await compiler.compile(FILES);
  allOk &= line('Compilación', compiled.success, compiled.success ? compiled.message : compiled.error);

  const broken = { 'Roto.java': 'public class Roto { public static void main(String[] a) { int x = "no" } }' };
  const bad = await compiler.compile(broken);
  const diagOk = !bad.success && Array.isArray(bad.diagnostics) && bad.diagnostics.length > 0;
  allOk &= line('Diagnósticos de error', diagOk,
    diagOk ? `${bad.diagnostics.length} -> linea ${bad.diagnostics[0].line} col ${bad.diagnostics[0].column}: ${bad.diagnostics[0].message}` : (bad.error || '').slice(0, 120));

  const salida = [];
  const runResult = await compiler.run(FILES, null, [], {
    onOutput: ({ stream, text }) => { if (stream !== 'system') salida.push(text); },
    onExit: () => {}
  });
  allOk &= line('Lanzamiento de la ejecución', runResult.success, runResult.success ? `main=${runResult.mainClass}` : runResult.error);
  await new Promise((r) => setTimeout(r, 6000));
  const texto = salida.join('');
  allOk &= line('Salida del programa', texto.includes('Hola desde Code Weaver'), JSON.stringify(texto.trim()).slice(0, 200));
  allOk &= line('Acentos correctos', texto.includes('ñ á é í ó ú'));

  const jar = await compiler.buildJar(FILES, 'selftest.jar', null, true);
  allOk &= line('Generación de fat JAR', jar.success, jar.success ? `${jar.jarPath} (${jar.sizeKb} KB, libs: ${jar.libsIncluded.length})` : jar.error);

  if (jar.success) {
    const { execFileSync } = require('node:child_process');
    try {
      const out = execFileSync(active.java, ['-Dstdout.encoding=UTF-8', '-jar', jar.jarPath], { encoding: 'utf8', timeout: 30000 });
      allOk &= line('java -jar del fat JAR', out.includes('Hola desde Code Weaver'), JSON.stringify(out.trim()).slice(0, 160));
    } catch (err) {
      allOk &= line('java -jar del fat JAR', false, String(err.message).slice(0, 200));
    }
  }

  const saved = await projects.save('Prueba interna', FILES, []);
  const loaded = await projects.load('Prueba interna');
  allOk &= line('Guardar y cargar proyecto', saved.success && loaded.success && Object.keys(loaded.files).length === 2);
  await projects.remove('Prueba interna');

  const det = deps.detectRequired({ 'X.java': 'import com.google.gson.Gson;\nimport java.util.List;' });
  allOk &= line('Detección de dependencias', det.required.includes('gson'), JSON.stringify(det));

  console.log(allOk ? '\n=== RESULTADO: TODO CORRECTO ===' : '\n=== RESULTADO: HAY FALLOS ===');
  process.exit(allOk ? 0 : 1);
})().catch((err) => { console.error('EXCEPCION:', err); process.exit(2); });
