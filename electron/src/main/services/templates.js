'use strict';

const path = require('node:path');
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const workspace = require('./workspace');

function list() {
  const dir = workspace.templatesDir();
  if (!fs.existsSync(dir)) return [];
  const result = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const metaPath = path.join(dir, entry.name, 'template.json');
    let meta = { id: entry.name, name: entry.name, description: '', category: 'Otras', order: 50 };
    if (fs.existsSync(metaPath)) {
      try { meta = { ...meta, ...JSON.parse(fs.readFileSync(metaPath, 'utf8')) }; } catch { /* usa el valor por defecto */ }
    }
    const javaFiles = fs.readdirSync(path.join(dir, entry.name)).filter((f) => f.endsWith('.java'));
    result.push({ ...meta, id: entry.name, fileCount: javaFiles.length, files: javaFiles });
  }
  return result.sort((a, b) => (a.order - b.order) || a.name.localeCompare(b.name));
}

async function load(id) {
  const dir = path.join(workspace.templatesDir(), path.basename(String(id || '')));
  if (!fs.existsSync(dir)) return { success: false, error: 'La plantilla no existe' };

  let meta = { name: path.basename(dir) };
  const metaPath = path.join(dir, 'template.json');
  if (fs.existsSync(metaPath)) {
    try { meta = { ...meta, ...JSON.parse(await fsp.readFile(metaPath, 'utf8')) }; } catch { /* usa el valor por defecto */ }
  }

  const files = {};
  for (const name of (await fsp.readdir(dir)).filter((f) => f.endsWith('.java'))) {
    files[name] = await fsp.readFile(path.join(dir, name), 'utf8');
  }
  if (!Object.keys(files).length) return { success: false, error: 'La plantilla no contiene archivos .java' };

  return { success: true, name: meta.name, notes: meta.notes || '', requiresLibs: meta.requiresLibs || [], files };
}

module.exports = { list, load };
