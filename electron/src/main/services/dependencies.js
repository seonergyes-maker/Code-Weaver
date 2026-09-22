'use strict';

const path = require('node:path');
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const workspace = require('./workspace');

const MAVEN_BASE = 'https://repo1.maven.org/maven2';

const COMMON_LIBRARIES = {
  'gson': { group: 'com.google.code.gson', artifact: 'gson', version: '2.10.1', desc: 'JSON de Google' },
  'jackson-core': { group: 'com.fasterxml.jackson.core', artifact: 'jackson-core', version: '2.16.1', desc: 'Núcleo Jackson' },
  'jackson-databind': { group: 'com.fasterxml.jackson.core', artifact: 'jackson-databind', version: '2.16.1', desc: 'Mapeo JSON <-> POJO' },
  'jackson-annotations': { group: 'com.fasterxml.jackson.core', artifact: 'jackson-annotations', version: '2.16.1', desc: 'Anotaciones Jackson' },
  'commons-lang3': { group: 'org.apache.commons', artifact: 'commons-lang3', version: '3.14.0', desc: 'Utilidades de lenguaje' },
  'commons-io': { group: 'commons-io', artifact: 'commons-io', version: '2.15.1', desc: 'Utilidades de E/S' },
  'commons-net': { group: 'commons-net', artifact: 'commons-net', version: '3.10.0', desc: 'FTP, Telnet, NTP' },
  'slf4j-api': { group: 'org.slf4j', artifact: 'slf4j-api', version: '2.0.11', desc: 'API de logging' },
  'slf4j-simple': { group: 'org.slf4j', artifact: 'slf4j-simple', version: '2.0.11', desc: 'Logging simple' },
  'logback-classic': { group: 'ch.qos.logback', artifact: 'logback-classic', version: '1.4.14', desc: 'Backend de logging' },
  'junit': { group: 'junit', artifact: 'junit', version: '4.13.2', desc: 'Tests JUnit 4' },
  'junit-jupiter': { group: 'org.junit.jupiter', artifact: 'junit-jupiter-api', version: '5.10.2', desc: 'Tests JUnit 5' },
  'lombok': { group: 'org.projectlombok', artifact: 'lombok', version: '1.18.30', desc: 'Menos boilerplate' },
  'guava': { group: 'com.google.guava', artifact: 'guava', version: '33.0.0-jre', desc: 'Utilidades de Google' },
  'okhttp': { group: 'com.squareup.okhttp3', artifact: 'okhttp', version: '4.12.0', desc: 'Cliente HTTP' },
  'httpclient': { group: 'org.apache.httpcomponents.client5', artifact: 'httpclient5', version: '5.3', desc: 'Cliente HTTP Apache' },
  'mysql-connector': { group: 'com.mysql', artifact: 'mysql-connector-j', version: '8.3.0', desc: 'JDBC MySQL' },
  'postgresql': { group: 'org.postgresql', artifact: 'postgresql', version: '42.7.1', desc: 'JDBC PostgreSQL' },
  'sqlite-jdbc': { group: 'org.xerial', artifact: 'sqlite-jdbc', version: '3.45.1.0', desc: 'JDBC SQLite' },
  'ojdbc11': { group: 'com.oracle.database.jdbc', artifact: 'ojdbc11', version: '23.3.0.23.09', desc: 'JDBC Oracle' },
  'jserialcomm': { group: 'com.fazecast', artifact: 'jSerialComm', version: '2.10.4', desc: 'Puerto serie RS-232' },
  'modbus4j': { group: 'com.infiniteautomation', artifact: 'modbus4j', version: '3.0.5', desc: 'Modbus TCP/RTU' },
  'jamod': { group: 'net.wimpi', artifact: 'jamod', version: '1.2', desc: 'Modbus (jamod)' },
  'javafx-base': { group: 'org.openjfx', artifact: 'javafx-base', version: '21', desc: 'JavaFX base' },
  'javafx-controls': { group: 'org.openjfx', artifact: 'javafx-controls', version: '21', desc: 'JavaFX controles' },
  'javafx-graphics': { group: 'org.openjfx', artifact: 'javafx-graphics', version: '21', desc: 'JavaFX gráficos' },
  'javafx-fxml': { group: 'org.openjfx', artifact: 'javafx-fxml', version: '21', desc: 'JavaFX FXML' },
  'javafx-media': { group: 'org.openjfx', artifact: 'javafx-media', version: '21', desc: 'JavaFX media' },
  'javafx-web': { group: 'org.openjfx', artifact: 'javafx-web', version: '21', desc: 'JavaFX WebView' }
};

const IMPORT_TO_LIBRARY = {
  'com.google.gson': 'gson',
  'com.fasterxml.jackson.databind': 'jackson-databind',
  'com.fasterxml.jackson.annotation': 'jackson-annotations',
  'com.fasterxml.jackson': 'jackson-core',
  'org.apache.commons.lang3': 'commons-lang3',
  'org.apache.commons.io': 'commons-io',
  'org.apache.commons.net': 'commons-net',
  'org.slf4j': 'slf4j-api',
  'ch.qos.logback': 'logback-classic',
  'org.junit.jupiter': 'junit-jupiter',
  'org.junit': 'junit',
  'junit.framework': 'junit',
  'lombok': 'lombok',
  'com.google.common': 'guava',
  'okhttp3': 'okhttp',
  'org.apache.http': 'httpclient',
  'com.mysql': 'mysql-connector',
  'org.postgresql': 'postgresql',
  'org.sqlite': 'sqlite-jdbc',
  'oracle.jdbc': 'ojdbc11',
  'com.fazecast.jSerialComm': 'jserialcomm',
  'com.serotonin.modbus4j': 'modbus4j',
  'net.wimpi.modbus': 'jamod',
  'javafx.application': 'javafx-base',
  'javafx.beans': 'javafx-base',
  'javafx.collections': 'javafx-base',
  'javafx.event': 'javafx-base',
  'javafx.util': 'javafx-base',
  'javafx.stage': 'javafx-graphics',
  'javafx.geometry': 'javafx-graphics',
  'javafx.concurrent': 'javafx-graphics',
  'javafx.css': 'javafx-graphics',
  'javafx.animation': 'javafx-graphics',
  'javafx.scene.control': 'javafx-controls',
  'javafx.scene': 'javafx-controls',
  'javafx.fxml': 'javafx-fxml',
  'javafx.media': 'javafx-media',
  'javafx.scene.web': 'javafx-web'
};

const JAVAFX_MODULES = ['javafx-base', 'javafx-controls', 'javafx-graphics', 'javafx-fxml'];

// Librerías que no están en Maven Central y deben aportarse a mano.
const MANUAL_ONLY = {
  'Symbol.RFID.API3': 'SDK oficial de Zebra (RFID Host Java SDK). Se instala con la app o se añade a mano.'
};

function mavenUrl(group, artifact, version) {
  return `${MAVEN_BASE}/${group.replace(/\./g, '/')}/${artifact}/${version}/${artifact}-${version}.jar`;
}

function listInstalled() {
  const dir = workspace.libsDir();
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir)
    .filter((f) => f.toLowerCase().endsWith('.jar'))
    .map((f) => {
      const full = path.join(dir, f);
      const stat = fs.statSync(full);
      return {
        name: f,
        path: full,
        sizeKb: Math.round(stat.size / 1024),
        manual: Boolean(MANUAL_ONLY[f.replace(/\.jar$/i, '')])
      };
    })
    .sort((a, b) => a.name.localeCompare(b.name));
}

/** Classpath con todos los JAR instalados, separados por ; en Windows. */
function classpath() {
  return listInstalled().map((l) => l.path).join(path.delimiter);
}

function jarFiles() {
  return listInstalled().map((l) => l.path);
}

async function downloadJar(group, artifact, version) {
  const fileName = `${artifact}-${version}.jar`;
  const dest = path.join(workspace.libsDir(), fileName);
  if (fs.existsSync(dest)) {
    return { success: true, alreadyInstalled: true, name: fileName, message: `${fileName} ya está instalada` };
  }
  const url = mavenUrl(group, artifact, version);
  const response = await fetch(url, { redirect: 'follow' });
  if (!response.ok) {
    return { success: false, error: `Maven Central respondió ${response.status} para ${artifact}-${version}` };
  }
  const buffer = Buffer.from(await response.arrayBuffer());
  if (buffer.length < 200) {
    return { success: false, error: `El archivo descargado de ${artifact} está vacío` };
  }
  const tmp = `${dest}.part`;
  await fsp.writeFile(tmp, buffer);
  await fsp.rename(tmp, dest);
  return { success: true, name: fileName, sizeKb: Math.round(buffer.length / 1024), message: `${fileName} instalada` };
}

async function install(name) {
  const key = String(name || '').toLowerCase().trim();
  const lib = COMMON_LIBRARIES[key];
  if (!lib) {
    return { success: false, error: `Librería desconocida: ${name}. Usa la instalación por coordenadas Maven.` };
  }
  try {
    return await downloadJar(lib.group, lib.artifact, lib.version);
  } catch (err) {
    return { success: false, error: `No se pudo descargar ${name}: ${err.message}` };
  }
}

async function installCustom({ group, artifact, version }) {
  if (!group || !artifact || !version) {
    return { success: false, error: 'Indica groupId, artifactId y versión' };
  }
  try {
    return await downloadJar(group.trim(), artifact.trim(), version.trim());
  } catch (err) {
    return { success: false, error: `No se pudo descargar ${artifact}: ${err.message}` };
  }
}

async function installLocalJars(paths) {
  const added = [];
  const errors = [];
  for (const src of paths) {
    try {
      const dest = path.join(workspace.libsDir(), path.basename(src));
      await fsp.copyFile(src, dest);
      added.push(path.basename(src));
    } catch (err) {
      errors.push(`${path.basename(src)}: ${err.message}`);
    }
  }
  return { success: errors.length === 0, added, errors };
}

async function remove(jarName) {
  const target = path.join(workspace.libsDir(), path.basename(jarName));
  if (!fs.existsSync(target)) return { success: false, error: 'La librería no existe' };
  try {
    await fsp.unlink(target);
    return { success: true, message: `${path.basename(jarName)} eliminada` };
  } catch (err) {
    return { success: false, error: err.message };
  }
}

/** Detecta librerías necesarias leyendo los imports del código. */
function detectRequired(files) {
  const code = Object.values(files || {}).join('\n');
  const imports = [...code.matchAll(/^\s*import\s+(?:static\s+)?([\w.]+)/gm)].map((m) => m[1]);
  const required = new Set();
  let hasJavaFx = false;

  const prefixes = Object.keys(IMPORT_TO_LIBRARY).sort((a, b) => b.length - a.length);
  for (const imp of imports) {
    for (const prefix of prefixes) {
      if (imp === prefix || imp.startsWith(`${prefix}.`)) {
        required.add(IMPORT_TO_LIBRARY[prefix]);
        if (imp.startsWith('javafx.')) hasJavaFx = true;
        break;
      }
    }
  }
  if (hasJavaFx) JAVAFX_MODULES.forEach((m) => required.add(m));

  const installed = listInstalled().map((l) => l.name.toLowerCase());
  const missing = [...required].filter((name) => {
    const lib = COMMON_LIBRARIES[name];
    if (!lib) return false;
    return !installed.some((jar) => jar.startsWith(`${lib.artifact.toLowerCase()}-`));
  });

  return { required: [...required], missing };
}

async function autoInstall(files) {
  const { missing } = detectRequired(files);
  if (!missing.length) {
    return { success: true, installed: [], message: 'Todas las dependencias detectadas ya estaban instaladas' };
  }
  const installed = [];
  const errors = [];
  for (const name of missing) {
    const result = await install(name);
    if (result.success && !result.alreadyInstalled) installed.push(result.name);
    else if (!result.success) errors.push(`${name}: ${result.error}`);
  }
  return {
    success: errors.length === 0,
    installed,
    errors,
    message: installed.length ? `Instaladas: ${installed.join(', ')}` : 'Sin cambios en las librerías'
  };
}

function catalog() {
  const installed = listInstalled().map((l) => l.name.toLowerCase());
  return Object.entries(COMMON_LIBRARIES).map(([key, lib]) => ({
    key,
    ...lib,
    installed: installed.some((jar) => jar.startsWith(`${lib.artifact.toLowerCase()}-`))
  }));
}

module.exports = {
  COMMON_LIBRARIES, catalog, listInstalled, classpath, jarFiles,
  install, installCustom, installLocalJars, remove, detectRequired, autoInstall
};
