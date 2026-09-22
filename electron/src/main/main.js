'use strict';

const { app, BrowserWindow, protocol, net, shell, dialog, Menu } = require('electron');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { pathToFileURL } = require('node:url');

const settings = require('./services/settings');
const workspace = require('./services/workspace');
const { registerIpc } = require('./ipc');

const APP_ROOT = path.join(__dirname, '..', '..');
const isDev = process.argv.includes('--dev');

/**
 * Detecta si la app vive en una unidad de red (UNC o letra mapeada).
 * Chromium no puede lanzar el proceso de GPU con el sandbox activo desde una
 * unidad de red: falla con "GPU process launch failed: error_code=18" y la
 * aplicación se cierra. Relajar SOLO el sandbox de la GPU lo soluciona y deja
 * intacto el sandbox del renderer, que es el que realmente importa.
 */
function isOnNetworkPath(target) {
  if (process.platform !== 'win32') return false;
  if (target.startsWith('\\\\')) return true;
  const drive = target.slice(0, 2);
  if (!/^[A-Za-z]:$/.test(drive)) return false;
  try {
    execFileSync('reg', ['query', `HKCU\\Network\\${drive[0].toUpperCase()}`], {
      stdio: 'ignore',
      windowsHide: true,
      timeout: 4000
    });
    return true;
  } catch {
    return false;
  }
}

if (isOnNetworkPath(APP_ROOT)) {
  app.commandLine.appendSwitch('disable-gpu-sandbox');
}

// Rutas servidas por el protocolo app:// (evita las limitaciones de file://,
// necesario para que los web workers de Monaco funcionen).
const ROUTES = [
  { prefix: '/monaco/', dir: path.join(APP_ROOT, 'node_modules', 'monaco-editor', 'min', 'vs') },
  { prefix: '/', dir: path.join(APP_ROOT, 'src', 'renderer') }
];

protocol.registerSchemesAsPrivileged([
  {
    scheme: 'app',
    privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true }
  }
]);

let mainWindow = null;

function resolveRoute(pathname) {
  const clean = decodeURIComponent(pathname).replace(/^\/+/, '/');
  for (const route of ROUTES) {
    if (clean.startsWith(route.prefix)) {
      const rel = clean.slice(route.prefix.length) || 'index.html';
      const full = path.join(route.dir, rel);
      // Impide salir del directorio servido
      if (!full.startsWith(route.dir)) return null;
      return full;
    }
  }
  return null;
}

function registerAppProtocol() {
  protocol.handle('app', (request) => {
    const url = new URL(request.url);
    const target = resolveRoute(url.pathname);
    if (!target) {
      return new Response('Not found', { status: 404 });
    }
    return net.fetch(pathToFileURL(target).toString());
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1500,
    height: 950,
    minWidth: 1000,
    minHeight: 640,
    show: false,
    backgroundColor: '#14161a',
    title: 'Code Weaver',
    icon: path.join(APP_ROOT, 'build', 'icon.ico'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      spellcheck: false
    }
  });

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
    if (isDev) mainWindow.webContents.openDevTools({ mode: 'detach' });
  });

  mainWindow.on('closed', () => { mainWindow = null; });

  // Los enlaces externos se abren en el navegador del sistema
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });

  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (!url.startsWith('app://')) {
      event.preventDefault();
      if (/^https?:/.test(url)) shell.openExternal(url);
    }
  });

  mainWindow.loadURL('app://local/index.html');
}

function buildMenu() {
  const template = [
    {
      label: 'Archivo',
      submenu: [
        { label: 'Nuevo proyecto', accelerator: 'CmdOrCtrl+N', click: () => send('menu:new-project') },
        { label: 'Abrir proyecto...', accelerator: 'CmdOrCtrl+O', click: () => send('menu:open-project') },
        { label: 'Guardar proyecto', accelerator: 'CmdOrCtrl+S', click: () => send('menu:save-project') },
        { type: 'separator' },
        { label: 'Importar carpeta con .java...', click: () => send('menu:import-folder') },
        { label: 'Exportar proyecto (ZIP)...', click: () => send('menu:export-zip') },
        { type: 'separator' },
        { label: 'Preferencias', accelerator: 'CmdOrCtrl+,', click: () => send('menu:settings') },
        { type: 'separator' },
        { role: 'quit', label: 'Salir' }
      ]
    },
    {
      label: 'Editar',
      submenu: [
        { role: 'undo', label: 'Deshacer' },
        { role: 'redo', label: 'Rehacer' },
        { type: 'separator' },
        { role: 'cut', label: 'Cortar' },
        { role: 'copy', label: 'Copiar' },
        { role: 'paste', label: 'Pegar' },
        { role: 'selectAll', label: 'Seleccionar todo' }
      ]
    },
    {
      label: 'Proyecto',
      submenu: [
        { label: 'Compilar', accelerator: 'CmdOrCtrl+B', click: () => send('menu:compile') },
        { label: 'Ejecutar', accelerator: 'F5', click: () => send('menu:run') },
        { label: 'Detener', accelerator: 'Shift+F5', click: () => send('menu:stop') },
        { label: 'Generar JAR', accelerator: 'CmdOrCtrl+Shift+B', click: () => send('menu:jar') },
        { type: 'separator' },
        { label: 'Gestor de librerías', click: () => send('menu:libraries') }
      ]
    },
    {
      label: 'Ver',
      submenu: [
        { role: 'reload', label: 'Recargar' },
        { role: 'toggleDevTools', label: 'Herramientas de desarrollo' },
        { type: 'separator' },
        { role: 'resetZoom', label: 'Zoom normal' },
        { role: 'zoomIn', label: 'Aumentar zoom' },
        { role: 'zoomOut', label: 'Reducir zoom' },
        { type: 'separator' },
        { role: 'togglefullscreen', label: 'Pantalla completa' }
      ]
    },
    {
      label: 'Ayuda',
      submenu: [
        {
          label: 'Abrir carpeta de datos',
          click: () => shell.openPath(workspace.userDataDir())
        },
        {
          label: 'Acerca de Code Weaver',
          click: () => {
            dialog.showMessageBox(mainWindow, {
              type: 'info',
              title: 'Code Weaver',
              message: `Code Weaver ${app.getVersion()}`,
              detail: 'IDE Java de escritorio con compilador integrado y asistente Claude.\nDaemon4.'
            });
          }
        }
      ]
    }
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

function send(channel, payload) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send(channel, payload);
  }
}

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });

  app.whenReady().then(async () => {
    registerAppProtocol();
    settings.init();
    await workspace.init(APP_ROOT);
    registerIpc({ getWindow: () => mainWindow, appRoot: APP_ROOT });
    buildMenu();
    createWindow();

    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });

  app.on('window-all-closed', () => {
    app.quit();
  });
}

module.exports = { send };
