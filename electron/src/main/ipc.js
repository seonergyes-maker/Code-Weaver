'use strict';

const { ipcMain, dialog, shell, app } = require('electron');
const path = require('node:path');

const settings = require('./services/settings');
const workspace = require('./services/workspace');
const jdk = require('./services/jdk');
const compiler = require('./services/javaCompiler');
const deps = require('./services/dependencies');
const projects = require('./services/projects');
const templates = require('./services/templates');
const assistant = require('./ai/assistant');

/** Envuelve un handler para que nunca reviente el renderer. */
function handle(channel, fn) {
  ipcMain.handle(channel, async (_event, payload) => {
    try {
      return await fn(payload);
    } catch (err) {
      console.error(`[ipc:${channel}]`, err);
      return { success: false, error: err?.message || String(err), code: err?.code };
    }
  });
}

function registerIpc({ getWindow }) {
  const send = (channel, payload) => {
    const win = getWindow();
    if (win && !win.isDestroyed()) win.webContents.send(channel, payload);
  };

  // ------------------------------------------------------------- entorno
  handle('env:info', async () => {
    const active = await jdk.active();
    return {
      success: true,
      appVersion: app.getVersion(),
      electron: process.versions.electron,
      node: process.versions.node,
      platform: process.platform,
      jdk: active,
      dirs: {
        data: workspace.userDataDir(),
        libs: workspace.libsDir(),
        output: workspace.outputDir(),
        projects: workspace.projectsDir()
      },
      aiReady: assistant.isConfigured()
    };
  });

  handle('env:detect-jdks', async () => {
    jdk.invalidate();
    return { success: true, jdks: await jdk.detectAll() };
  });

  handle('env:choose-jdk', async () => {
    const result = await dialog.showOpenDialog(getWindow(), {
      title: 'Selecciona la carpeta del JDK (la que contiene bin\\javac)',
      properties: ['openDirectory']
    });
    if (result.canceled || !result.filePaths.length) return { success: false, cancelled: true };
    const home = result.filePaths[0];
    if (!jdk.isJdkHome(home)) {
      return { success: false, error: 'Esa carpeta no contiene bin/javac. Elige la raíz del JDK.' };
    }
    settings.set({ jdkHome: home });
    jdk.invalidate();
    return { success: true, jdk: await jdk.active() };
  });

  handle('env:open-path', async (p) => {
    await shell.openPath(p);
    return { success: true };
  });

  handle('env:show-item', async (p) => {
    shell.showItemInFolder(p);
    return { success: true };
  });

  // ------------------------------------------------------------- preferencias
  handle('settings:get', async () => ({ success: true, settings: settings.publicView() }));
  handle('settings:set', async (patch) => {
    if (patch && 'jdkHome' in patch) jdk.invalidate();
    return { success: true, settings: settings.set(patch) };
  });

  // ------------------------------------------------------------- Java
  handle('java:compile', async ({ files }) => {
    if (settings.get('autoInstallDeps')) {
      const auto = await deps.autoInstall(files);
      if (auto.installed?.length) {
        send('java:output', { stream: 'system', text: `> Librerías instaladas: ${auto.installed.join(', ')}\n` });
      }
    }
    return compiler.compile(files);
  });

  handle('java:run', async ({ files, mainClass, args }) => {
    if (settings.get('autoInstallDeps')) {
      const auto = await deps.autoInstall(files);
      if (auto.installed?.length) {
        send('java:output', { stream: 'system', text: `> Librerías instaladas: ${auto.installed.join(', ')}\n` });
      }
    }
    return compiler.run(files, mainClass, args, {
      onOutput: (payload) => send('java:output', payload),
      onExit: (payload) => send('java:exit', payload)
    });
  });

  handle('java:stop', async () => compiler.stop());
  handle('java:stdin', async (text) => compiler.writeStdin(text));
  handle('java:main-classes', async ({ files }) => ({
    success: true,
    mainClasses: compiler.findMainClasses(files)
  }));

  handle('java:jar', async ({ files, jarName, mainClass, fat }) => {
    if (settings.get('autoInstallDeps')) await deps.autoInstall(files);
    return compiler.buildJar(files, jarName, mainClass, fat !== false);
  });

  // ------------------------------------------------------------- librerías
  handle('libs:list', async () => ({ success: true, libs: deps.listInstalled() }));
  handle('libs:catalog', async () => ({ success: true, catalog: deps.catalog() }));
  handle('libs:install', async (name) => deps.install(name));
  handle('libs:install-custom', async (coords) => deps.installCustom(coords));
  handle('libs:remove', async (jarName) => deps.remove(jarName));
  handle('libs:detect', async ({ files }) => ({ success: true, ...deps.detectRequired(files) }));
  handle('libs:auto-install', async ({ files }) => deps.autoInstall(files));
  handle('libs:open-folder', async () => {
    await shell.openPath(workspace.libsDir());
    return { success: true };
  });

  handle('libs:install-from-files', async () => {
    const result = await dialog.showOpenDialog(getWindow(), {
      title: 'Selecciona los archivos .jar',
      filters: [{ name: 'Librerías Java', extensions: ['jar'] }],
      properties: ['openFile', 'multiSelections']
    });
    if (result.canceled || !result.filePaths.length) return { success: false, cancelled: true };
    return deps.installLocalJars(result.filePaths);
  });

  // ------------------------------------------------------------- proyectos
  handle('projects:list', async () => ({ success: true, projects: projects.list() }));
  handle('projects:save', async ({ name, files, chatHistory }) => projects.save(name, files, chatHistory));
  handle('projects:load', async (name) => projects.load(name));
  handle('projects:delete', async (name) => projects.remove(name));
  handle('projects:rename', async ({ name, newName }) => projects.rename(name, newName));

  handle('projects:import-folder', async () => {
    const result = await dialog.showOpenDialog(getWindow(), {
      title: 'Selecciona la carpeta con archivos .java',
      properties: ['openDirectory']
    });
    if (result.canceled || !result.filePaths.length) return { success: false, cancelled: true };
    return projects.importFolder(result.filePaths[0]);
  });

  handle('projects:export-zip', async ({ name, files }) => {
    const result = await dialog.showSaveDialog(getWindow(), {
      title: 'Exportar proyecto',
      defaultPath: path.join(app.getPath('documents'), `${projects.slug(name)}.zip`),
      filters: [{ name: 'Archivo ZIP', extensions: ['zip'] }]
    });
    if (result.canceled || !result.filePath) return { success: false, cancelled: true };
    return projects.exportZip(name, files, result.filePath);
  });

  handle('projects:export-folder', async ({ files }) => {
    const result = await dialog.showOpenDialog(getWindow(), {
      title: 'Carpeta de destino para los .java',
      properties: ['openDirectory', 'createDirectory']
    });
    if (result.canceled || !result.filePaths.length) return { success: false, cancelled: true };
    return projects.exportFolder(files, result.filePaths[0]);
  });

  // ------------------------------------------------------------- plantillas
  handle('templates:list', async () => ({ success: true, templates: templates.list() }));
  handle('templates:load', async (id) => templates.load(id));

  // ------------------------------------------------------------- asistente
  const aiCall = (fn) => async (payload) => {
    send('ai:status', { busy: true });
    try {
      const result = await fn(payload);
      return { success: true, ...result };
    } finally {
      send('ai:status', { busy: false });
    }
  };

  handle('ai:available', async () => ({ success: true, ready: assistant.isConfigured() }));
  handle('ai:chat', aiCall(assistant.chat));
  handle('ai:fix-error', aiCall(assistant.fixError));
  handle('ai:explain-error', aiCall(assistant.explainError));
  handle('ai:generate-tests', aiCall(assistant.generateTests));
  handle('ai:generate-docs', aiCall(assistant.generateDocs));
  handle('ai:plan', aiCall(assistant.plan));
  handle('ai:analyze-output', aiCall(assistant.analyzeOutput));
  handle('ai:cancel', async () => ({ success: assistant.cancel() }));
}

module.exports = { registerIpc };
