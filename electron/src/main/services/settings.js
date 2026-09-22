'use strict';

const { app, safeStorage } = require('electron');
const path = require('node:path');
const fs = require('node:fs');

const DEFAULTS = {
  apiKey: '',            // se guarda cifrada cuando el SO lo permite
  baseUrl: '',           // opcional: proxy/gateway compatible con la API de Anthropic
  model: 'claude-sonnet-4-5',
  useAgents: true,
  jdkHome: '',           // vacío = detección automática
  javaTarget: '21',      // --release de javac
  autoInstallDeps: true,
  runTimeoutSec: 0,      // 0 = sin límite (la app usa el botón Detener)
  fontSize: 14,
  wordWrap: false,
  lastProject: ''
};

let file = null;
let data = { ...DEFAULTS };

function init() {
  file = path.join(app.getPath('userData'), 'settings.json');
  try {
    if (fs.existsSync(file)) {
      const raw = JSON.parse(fs.readFileSync(file, 'utf8'));
      data = { ...DEFAULTS, ...raw };
      data.apiKey = decryptKey(raw);
    }
  } catch (err) {
    console.error('No se pudo leer settings.json:', err.message);
    data = { ...DEFAULTS };
  }
  return data;
}

function decryptKey(raw) {
  if (raw.apiKeyEnc) {
    try {
      if (safeStorage.isEncryptionAvailable()) {
        return safeStorage.decryptString(Buffer.from(raw.apiKeyEnc, 'base64'));
      }
    } catch (err) {
      console.error('No se pudo descifrar la API key:', err.message);
    }
    return '';
  }
  return raw.apiKey || '';
}

function persist() {
  const out = { ...data };
  delete out.apiKey;
  delete out.apiKeyEnc;
  if (data.apiKey) {
    try {
      if (safeStorage.isEncryptionAvailable()) {
        out.apiKeyEnc = safeStorage.encryptString(data.apiKey).toString('base64');
      } else {
        out.apiKey = data.apiKey;
      }
    } catch {
      out.apiKey = data.apiKey;
    }
  }
  fs.writeFileSync(file, JSON.stringify(out, null, 2), 'utf8');
}

function all() {
  return { ...data };
}

/** Versión segura para el renderer: nunca expone la clave completa. */
function publicView() {
  const view = { ...data };
  view.hasApiKey = Boolean(data.apiKey);
  view.apiKey = data.apiKey ? `${data.apiKey.slice(0, 7)}...${data.apiKey.slice(-4)}` : '';
  return view;
}

function get(key) {
  return data[key];
}

function set(patch) {
  for (const [key, value] of Object.entries(patch || {})) {
    if (key in DEFAULTS) data[key] = value;
  }
  persist();
  return publicView();
}

module.exports = { init, all, get, set, publicView, DEFAULTS };
