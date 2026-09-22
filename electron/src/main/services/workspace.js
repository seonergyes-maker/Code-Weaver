'use strict';

const { app } = require('electron');
const path = require('node:path');
const fs = require('node:fs');
const fsp = require('node:fs/promises');

let dirs = null;
let appRoot = null;

function ensureSync(dir) {
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

async function init(rootDir) {
  appRoot = rootDir;
  const base = app.getPath('userData');
  dirs = {
    base,
    libs: ensureSync(path.join(base, 'libs')),
    projects: ensureSync(path.join(base, 'projects')),
    output: ensureSync(path.join(base, 'output')),
    build: ensureSync(path.join(base, 'build')),
    logs: ensureSync(path.join(base, 'logs'))
  };
  await copyBundledLibs();
  return dirs;
}

/**
 * En el primer arranque copia las librerías que viajan con la app
 * (SDK de Zebra, llrp4j, gson...) a la carpeta de datos del usuario,
 * porque dentro del .asar no se pueden usar como classpath.
 */
async function copyBundledLibs() {
  const src = path.join(appRoot, 'bundled-libs');
  if (!fs.existsSync(src)) return;
  const entries = await fsp.readdir(src);
  for (const name of entries) {
    if (!name.toLowerCase().endsWith('.jar')) continue;
    const dest = path.join(dirs.libs, name);
    if (fs.existsSync(dest)) continue;
    try {
      await fsp.copyFile(path.join(src, name), dest);
    } catch (err) {
      console.error('No se pudo copiar la librería', name, err.message);
    }
  }
}

const get = () => dirs;
const userDataDir = () => (dirs ? dirs.base : app.getPath('userData'));
const libsDir = () => dirs.libs;
const projectsDir = () => dirs.projects;
const outputDir = () => dirs.output;
const buildDir = () => dirs.build;
const templatesDir = () => path.join(appRoot, 'templates');
const root = () => appRoot;

module.exports = {
  init, get, userDataDir, libsDir, projectsDir, outputDir, buildDir, templatesDir, root, ensureSync
};
