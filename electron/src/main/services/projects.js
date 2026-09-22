'use strict';

const path = require('node:path');
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const AdmZip = require('adm-zip');
const workspace = require('./workspace');

function slug(name) {
  return String(name || 'proyecto')
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/[^a-zA-Z0-9-_ ]/g, '')
    .trim().replace(/\s+/g, '-')
    .toLowerCase() || 'proyecto';
}

function fileFor(name) {
  return path.join(workspace.projectsDir(), `${slug(name)}.json`);
}

function list() {
  const dir = workspace.projectsDir();
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir)
    .filter((f) => f.endsWith('.json'))
    .map((f) => {
      try {
        const data = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
        return {
          name: data.name,
          slug: f.replace(/\.json$/, ''),
          fileCount: Object.keys(data.files || {}).length,
          updatedAt: data.updatedAt || null,
          createdAt: data.createdAt || null
        };
      } catch {
        return null;
      }
    })
    .filter(Boolean)
    .sort((a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt)));
}

async function save(name, files, chatHistory) {
  if (!name || !String(name).trim()) return { success: false, error: 'El proyecto necesita un nombre' };
  const target = fileFor(name);
  let createdAt = new Date().toISOString();
  if (fs.existsSync(target)) {
    try { createdAt = JSON.parse(fs.readFileSync(target, 'utf8')).createdAt || createdAt; } catch { /* nuevo */ }
  }
  const payload = {
    name: String(name).trim(),
    files: files || {},
    chatHistory: chatHistory || [],
    createdAt,
    updatedAt: new Date().toISOString()
  };
  await fsp.writeFile(target, JSON.stringify(payload, null, 2), 'utf8');
  return { success: true, name: payload.name, path: target, message: `Proyecto "${payload.name}" guardado` };
}

async function load(name) {
  const target = fileFor(name);
  if (!fs.existsSync(target)) return { success: false, error: 'El proyecto no existe' };
  try {
    const data = JSON.parse(await fsp.readFile(target, 'utf8'));
    return { success: true, ...data };
  } catch (err) {
    return { success: false, error: `Proyecto dañado: ${err.message}` };
  }
}

async function remove(name) {
  const target = fileFor(name);
  if (!fs.existsSync(target)) return { success: false, error: 'El proyecto no existe' };
  await fsp.unlink(target);
  return { success: true, message: `Proyecto "${name}" eliminado` };
}

async function rename(name, newName) {
  const loaded = await load(name);
  if (!loaded.success) return loaded;
  const saved = await save(newName, loaded.files, loaded.chatHistory);
  if (!saved.success) return saved;
  if (slug(name) !== slug(newName)) await remove(name);
  return saved;
}

/** Lee todos los .java de una carpeta (incluyendo subcarpetas) como proyecto. */
async function importFolder(dir) {
  const files = {};
  const walk = async (current, depth = 0) => {
    if (depth > 6) return;
    const entries = await fsp.readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) {
        if (['node_modules', '.git', 'target', 'build', 'out', 'bin'].includes(entry.name)) continue;
        await walk(full, depth + 1);
      } else if (entry.name.toLowerCase().endsWith('.java')) {
        files[entry.name] = await fsp.readFile(full, 'utf8');
      }
    }
  };
  await walk(dir);
  if (!Object.keys(files).length) {
    return { success: false, error: 'No se han encontrado archivos .java en esa carpeta' };
  }
  return { success: true, name: path.basename(dir), files };
}

async function exportZip(name, files, destPath) {
  const zip = new AdmZip();
  for (const [fileName, code] of Object.entries(files || {})) {
    zip.addFile(fileName, Buffer.from(code, 'utf8'));
  }
  zip.writeZip(destPath);
  return { success: true, path: destPath, message: `Exportado a ${path.basename(destPath)}` };
}

async function exportFolder(files, destDir) {
  await fsp.mkdir(destDir, { recursive: true });
  const written = [];
  for (const [fileName, code] of Object.entries(files || {})) {
    const full = path.join(destDir, path.basename(fileName));
    await fsp.writeFile(full, code, 'utf8');
    written.push(path.basename(fileName));
  }
  return { success: true, path: destDir, written, message: `${written.length} archivo(s) guardados` };
}

module.exports = { list, save, load, remove, rename, importFolder, exportZip, exportFolder, slug };
