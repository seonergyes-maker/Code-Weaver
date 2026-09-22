'use strict';

const { contextBridge, ipcRenderer } = require('electron');

const invoke = (channel, payload) => ipcRenderer.invoke(channel, payload);

/** Suscripción a eventos push del proceso principal. Devuelve la función para cancelar. */
function on(channel, handler) {
  const listener = (_event, payload) => handler(payload);
  ipcRenderer.on(channel, listener);
  return () => ipcRenderer.removeListener(channel, listener);
}

contextBridge.exposeInMainWorld('api', {
  // ---- Entorno / configuración ----
  env: {
    info: () => invoke('env:info'),
    detectJdks: () => invoke('env:detect-jdks'),
    chooseJdk: () => invoke('env:choose-jdk'),
    openPath: (p) => invoke('env:open-path', p),
    showItem: (p) => invoke('env:show-item', p)
  },
  settings: {
    get: () => invoke('settings:get'),
    set: (patch) => invoke('settings:set', patch)
  },

  // ---- Compilación / ejecución ----
  java: {
    compile: (files) => invoke('java:compile', { files }),
    run: (files, mainClass, args) => invoke('java:run', { files, mainClass, args }),
    stop: () => invoke('java:stop'),
    sendStdin: (text) => invoke('java:stdin', text),
    jar: (files, jarName, mainClass, fat) => invoke('java:jar', { files, jarName, mainClass, fat }),
    findMainClasses: (files) => invoke('java:main-classes', { files }),
    onOutput: (handler) => on('java:output', handler),
    onExit: (handler) => on('java:exit', handler)
  },

  // ---- Librerías / dependencias ----
  libs: {
    list: () => invoke('libs:list'),
    catalog: () => invoke('libs:catalog'),
    install: (name) => invoke('libs:install', name),
    installCustom: (coords) => invoke('libs:install-custom', coords),
    installFromFiles: () => invoke('libs:install-from-files'),
    remove: (jarName) => invoke('libs:remove', jarName),
    detect: (files) => invoke('libs:detect', { files }),
    autoInstall: (files) => invoke('libs:auto-install', { files }),
    openFolder: () => invoke('libs:open-folder')
  },

  // ---- Proyectos ----
  projects: {
    list: () => invoke('projects:list'),
    save: (name, files, chatHistory) => invoke('projects:save', { name, files, chatHistory }),
    load: (name) => invoke('projects:load', name),
    remove: (name) => invoke('projects:delete', name),
    rename: (name, newName) => invoke('projects:rename', { name, newName }),
    importFolder: () => invoke('projects:import-folder'),
    exportZip: (name, files) => invoke('projects:export-zip', { name, files }),
    exportFolder: (files) => invoke('projects:export-folder', { files })
  },

  // ---- Plantillas ----
  templates: {
    list: () => invoke('templates:list'),
    load: (id) => invoke('templates:load', id)
  },

  // ---- Asistente Claude ----
  ai: {
    available: () => invoke('ai:available'),
    chat: (payload) => invoke('ai:chat', payload),
    fixError: (payload) => invoke('ai:fix-error', payload),
    explainError: (payload) => invoke('ai:explain-error', payload),
    generateTests: (payload) => invoke('ai:generate-tests', payload),
    generateDocs: (payload) => invoke('ai:generate-docs', payload),
    plan: (payload) => invoke('ai:plan', payload),
    analyzeOutput: (payload) => invoke('ai:analyze-output', payload),
    cancel: () => invoke('ai:cancel'),
    onStatus: (handler) => on('ai:status', handler)
  },

  // ---- Menú de la aplicación ----
  menu: {
    on: (channel, handler) => on(channel, handler)
  }
});
