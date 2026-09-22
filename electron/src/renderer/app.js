/* Code Weaver — proceso de renderizado */
'use strict';

(function () {

const api = window.api;

// ------------------------------------------------------------------ estado

const state = {
  files: {},
  current: null,
  dirty: new Set(),
  projectName: '',
  chat: [],
  models: new Map(),
  viewStates: new Map(),
  running: false,
  busyAi: false,
  settings: {},
  lastError: '',
  diagnostics: [],
  env: null
};

let editor = null;
let monacoRef = null;

// ------------------------------------------------------------------ utilidades

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => [...document.querySelectorAll(sel)];

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function toast(message, kind = 'info', ms = 3800) {
  const el = document.createElement('div');
  el.className = `toast ${kind}`;
  el.textContent = message;
  $('#toasts').appendChild(el);
  setTimeout(() => {
    el.style.opacity = '0';
    el.style.transition = 'opacity .2s';
    setTimeout(() => el.remove(), 220);
  }, ms);
}

/** Diálogo modal con campos; devuelve un objeto con los valores o null. */
function ask({ title, text = '', fields = [], okLabel = 'Aceptar' }) {
  return new Promise((resolve) => {
    const modal = $('#modal');
    $('#modal-title').textContent = title;
    $('#modal-text').textContent = text;
    $('#modal-text').hidden = !text;
    $('#modal-ok').textContent = okLabel;

    const container = $('#modal-fields');
    container.innerHTML = '';
    for (const field of fields) {
      const label = document.createElement('label');
      label.className = 'field';
      const span = document.createElement('span');
      span.textContent = field.label;
      const input = document.createElement('input');
      input.className = 'input';
      input.name = field.name;
      input.type = field.type || 'text';
      input.value = field.value || '';
      input.placeholder = field.placeholder || '';
      label.append(span, input);
      container.appendChild(label);
    }

    const onClose = () => {
      modal.removeEventListener('close', onClose);
      if (modal.returnValue !== 'ok') return resolve(null);
      const values = {};
      for (const input of container.querySelectorAll('input')) values[input.name] = input.value.trim();
      resolve(values);
    };
    modal.addEventListener('close', onClose);
    modal.showModal();
    const first = container.querySelector('input');
    if (first) { first.focus(); first.select(); }
  });
}

async function confirmAsk(title, text) {
  const result = await ask({ title, text, fields: [], okLabel: 'Sí, continuar' });
  return result !== null;
}

/** Markdown mínimo y seguro para las respuestas del asistente. */
function renderMarkdown(text) {
  const blocks = [];
  let html = escapeHtml(text || '')
    .replace(/```(\w*)\n?([\s\S]*?)```/g, (_m, lang, code) => {
      blocks.push(`<pre><code class="lang-${escapeHtml(lang)}">${code.replace(/\n$/, '')}</code></pre>`);
      return `\u0000BLOCK${blocks.length - 1}\u0000`;
    });

  html = html
    .replace(/`([^`\n]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|\s)\*([^*\n]+)\*/g, '$1<em>$2</em>');

  const lines = html.split('\n');
  const out = [];
  let inList = false;
  for (const line of lines) {
    const listMatch = line.match(/^\s*(?:[-*]|\d+\.)\s+(.*)$/);
    if (listMatch) {
      if (!inList) { out.push('<ul>'); inList = true; }
      out.push(`<li>${listMatch[1]}</li>`);
      continue;
    }
    if (inList) { out.push('</ul>'); inList = false; }
    const heading = line.match(/^(#{1,4})\s+(.*)$/);
    if (heading) { out.push(`<p><strong>${heading[2]}</strong></p>`); continue; }
    if (line.trim() === '') { out.push(''); continue; }
    out.push(`<p>${line}</p>`);
  }
  if (inList) out.push('</ul>');

  return out.join('\n').replace(/\u0000BLOCK(\d+)\u0000/g, (_m, i) => blocks[Number(i)]);
}

// ------------------------------------------------------------------ editor

function initEditor() {
  return new Promise((resolve) => {
    require.config({ paths: { vs: 'app://local/monaco' } });
    require(['vs/editor/editor.main'], (monaco) => {
      monacoRef = monaco;
      monaco.editor.defineTheme('code-weaver', {
        base: 'vs-dark',
        inherit: true,
        rules: [
          { token: 'comment', foreground: '6b7483', fontStyle: 'italic' },
          { token: 'keyword', foreground: 'e0765f' },
          { token: 'string', foreground: '9ecb8a' },
          { token: 'number', foreground: 'd8a640' },
          { token: 'type', foreground: '7cb3e0' }
        ],
        colors: {
          'editor.background': '#14161a',
          'editor.lineHighlightBackground': '#1b1f26',
          'editorLineNumber.foreground': '#4b5361',
          'editorGutter.background': '#14161a',
          'editorIndentGuide.background1': '#262c35'
        }
      });

      editor = monaco.editor.create($('#editor'), {
        theme: 'code-weaver',
        language: 'java',
        automaticLayout: true,
        fontSize: state.settings.fontSize || 14,
        fontFamily: "'Cascadia Code','JetBrains Mono',Consolas,monospace",
        wordWrap: state.settings.wordWrap ? 'on' : 'off',
        minimap: { enabled: true, maxColumn: 70 },
        scrollBeyondLastLine: false,
        renderLineHighlight: 'all',
        smoothScrolling: true,
        tabSize: 4,
        bracketPairColorization: { enabled: true }
      });

      editor.onDidChangeModelContent(() => {
        if (!state.current) return;
        state.files[state.current] = editor.getValue();
        state.dirty.add(state.current);
        renderTabs();
      });

      resolve();
    });
  });
}

function modelFor(name) {
  if (!state.models.has(name)) {
    const uri = monacoRef.Uri.parse(`inmemory://project/${name}`);
    const existing = monacoRef.editor.getModel(uri);
    const model = existing || monacoRef.editor.createModel(state.files[name] ?? '', 'java', uri);
    state.models.set(name, model);
  }
  const model = state.models.get(name);
  if (model.getValue() !== state.files[name]) model.setValue(state.files[name] ?? '');
  return model;
}

function openFile(name) {
  if (!(name in state.files)) return;
  if (state.current && editor) state.viewStates.set(state.current, editor.saveViewState());
  state.current = name;
  editor.setModel(modelFor(name));
  const view = state.viewStates.get(name);
  if (view) editor.restoreViewState(view);
  editor.focus();
  renderTabs();
  renderFileList();
  applyDiagnostics();
}

function closeFile(name) {
  const names = Object.keys(state.files);
  if (names.length <= 1) { toast('El proyecto debe tener al menos un archivo', 'err'); return; }
  delete state.files[name];
  const model = state.models.get(name);
  if (model) { model.dispose(); state.models.delete(name); }
  state.viewStates.delete(name);
  state.dirty.delete(name);
  if (state.current === name) openFile(Object.keys(state.files)[0]);
  else { renderTabs(); renderFileList(); }
  refreshMainClasses();
}

function setFiles(files, { projectName, chat } = {}) {
  for (const model of state.models.values()) model.dispose();
  state.models.clear();
  state.viewStates.clear();
  state.dirty.clear();
  state.files = { ...files };
  if (projectName !== undefined) {
    state.projectName = projectName;
    $('#project-name').textContent = projectName || 'Proyecto sin guardar';
  }
  if (chat) { state.chat = chat; renderChat(); }
  const first = Object.keys(state.files)[0];
  state.current = null;
  if (first) openFile(first);
  renderTabs();
  renderFileList();
  refreshMainClasses();
}

// ------------------------------------------------------------------ pestañas y lista

function renderTabs() {
  const tabs = $('#tabs');
  tabs.innerHTML = '';
  for (const name of Object.keys(state.files)) {
    const tab = document.createElement('button');
    tab.className = `tab${name === state.current ? ' is-active' : ''}${state.dirty.has(name) ? ' is-dirty' : ''}`;
    tab.innerHTML = `<span class="dot"></span><span>${escapeHtml(name)}</span><span class="close" title="Cerrar">×</span>`;
    tab.addEventListener('click', (event) => {
      if (event.target.classList.contains('close')) closeFile(name);
      else openFile(name);
    });
    tabs.appendChild(tab);
  }
}

function renderFileList() {
  const list = $('#file-list');
  list.innerHTML = '';
  const names = Object.keys(state.files).sort((a, b) => a.localeCompare(b));
  if (!names.length) {
    list.innerHTML = '<li class="empty">Sin archivos</li>';
    return;
  }
  const errorFiles = new Set(state.diagnostics.filter((d) => d.severity === 'error').map((d) => d.file));
  for (const name of names) {
    const item = document.createElement('li');
    item.className = `list-item${name === state.current ? ' is-active' : ''}${errorFiles.has(name) ? ' has-error' : ''}`;
    item.innerHTML = `
      <span class="name">${escapeHtml(name)}${state.dirty.has(name) ? ' •' : ''}</span>
      <span class="row-actions">
        <button class="icon-btn" data-act="rename" title="Renombrar">✎</button>
        <button class="icon-btn" data-act="delete" title="Eliminar">🗑</button>
      </span>`;
    item.addEventListener('click', async (event) => {
      const act = event.target.dataset?.act;
      if (act === 'rename') { event.stopPropagation(); await renameFile(name); }
      else if (act === 'delete') { event.stopPropagation(); closeFile(name); }
      else openFile(name);
    });
    list.appendChild(item);
  }
}

async function newFile() {
  const values = await ask({
    title: 'Nuevo archivo Java',
    fields: [{ name: 'name', label: 'Nombre de la clase', placeholder: 'MiClase', value: '' }]
  });
  if (!values || !values.name) return;
  let name = values.name;
  if (!name.endsWith('.java')) name += '.java';
  if (state.files[name]) { toast('Ya existe un archivo con ese nombre', 'err'); return; }
  const className = name.replace(/\.java$/, '');
  state.files[name] = `public class ${className} {\n\n    public ${className}() {\n    }\n}\n`;
  state.dirty.add(name);
  openFile(name);
  refreshMainClasses();
}

async function renameFile(name) {
  const values = await ask({
    title: 'Renombrar archivo',
    fields: [{ name: 'name', label: 'Nuevo nombre', value: name }]
  });
  if (!values || !values.name || values.name === name) return;
  let newName = values.name;
  if (!newName.endsWith('.java')) newName += '.java';
  if (state.files[newName]) { toast('Ya existe un archivo con ese nombre', 'err'); return; }

  state.files[newName] = state.files[name];
  delete state.files[name];
  const model = state.models.get(name);
  if (model) { model.dispose(); state.models.delete(name); }
  state.dirty.add(newName);
  state.dirty.delete(name);
  openFile(newName);
  refreshMainClasses();
}

// ------------------------------------------------------------------ consola

function appendConsole(text, stream = 'stdout') {
  const pane = $('#console-output');
  const span = document.createElement('span');
  if (stream === 'stderr') span.className = 'line-stderr';
  else if (stream === 'system') span.className = 'line-system';
  else if (stream === 'ok') span.className = 'line-ok';
  span.textContent = text;
  pane.appendChild(span);
  pane.scrollTop = pane.scrollHeight;
}

function clearConsole() {
  $('#console-output').innerHTML = '';
  state.lastError = '';
}

function showConsoleTab(tab) {
  $$('.console-tab').forEach((b) => b.classList.toggle('is-active', b.dataset.tab === tab));
  $$('.console-body').forEach((b) => b.classList.toggle('is-active', b.dataset.tab === tab));
}

function setDiagnostics(diagnostics) {
  state.diagnostics = diagnostics || [];
  const errors = state.diagnostics.filter((d) => d.severity === 'error').length;
  const pill = $('#problem-count');
  pill.textContent = String(state.diagnostics.length);
  pill.classList.toggle('has-errors', errors > 0);

  const container = $('#problems');
  container.innerHTML = '';
  if (!state.diagnostics.length) {
    container.innerHTML = '<div class="empty">Sin problemas detectados</div>';
  } else {
    for (const d of state.diagnostics) {
      const row = document.createElement('div');
      row.className = `problem ${d.severity}`;
      row.innerHTML = `
        <span class="sev">${d.severity === 'error' ? '✕' : '!'}</span>
        <span>
          ${escapeHtml(d.message)}
          <div class="where">${escapeHtml(d.file)}:${d.line}:${d.column}</div>
        </span>`;
      row.addEventListener('click', () => {
        if (state.files[d.file]) {
          openFile(d.file);
          editor.revealLineInCenter(d.line);
          editor.setPosition({ lineNumber: d.line, column: d.column });
          editor.focus();
        }
      });
      container.appendChild(row);
    }
  }
  applyDiagnostics();
  renderFileList();
}

function applyDiagnostics() {
  if (!monacoRef || !state.current) return;
  for (const [name, model] of state.models.entries()) {
    const markers = state.diagnostics
      .filter((d) => d.file === name)
      .map((d) => ({
        severity: d.severity === 'error' ? monacoRef.MarkerSeverity.Error : monacoRef.MarkerSeverity.Warning,
        message: d.message,
        startLineNumber: d.line,
        startColumn: d.column,
        endLineNumber: d.line,
        endColumn: d.column + 1
      }));
    monacoRef.editor.setModelMarkers(model, 'javac', markers);
  }
}

// ------------------------------------------------------------------ acciones Java

function setRunning(running) {
  state.running = running;
  $('#btn-run').disabled = running;
  $('#btn-stop').disabled = !running;
  $('#btn-compile').disabled = running;
  $('#btn-jar').disabled = running;
  $('#stdin-row').hidden = !running;
}

async function refreshMainClasses() {
  const result = await api.java.findMainClasses(state.files);
  const select = $('#main-class');
  const previous = select.value;
  select.innerHTML = '';
  const classes = result.mainClasses || [];
  if (!classes.length) {
    select.innerHTML = '<option value="">Sin método main</option>';
    return;
  }
  for (const entry of classes) {
    const option = document.createElement('option');
    option.value = entry.className;
    option.textContent = entry.className;
    select.appendChild(option);
  }
  if (classes.some((c) => c.className === previous)) select.value = previous;
}

async function doCompile() {
  showConsoleTab('output');
  clearConsole();
  appendConsole('> Compilando…\n', 'system');
  const result = await api.java.compile(state.files);
  if (result.success) {
    appendConsole(`${result.message}\n`, 'ok');
    if (result.warnings) appendConsole(`${result.warnings}\n`, 'stderr');
    setDiagnostics(result.diagnostics || []);
    toast('Compilación correcta', 'ok');
  } else {
    appendConsole(`${result.error}\n`, 'stderr');
    state.lastError = result.error;
    setDiagnostics(result.diagnostics || []);
    showConsoleTab(result.diagnostics?.length ? 'problems' : 'output');
    toast('Error de compilación', 'err');
  }
}

async function doRun() {
  showConsoleTab('output');
  clearConsole();
  setRunning(true);
  const result = await api.java.run(state.files, $('#main-class').value || null, []);
  if (!result.success) {
    setRunning(false);
    appendConsole(`${result.error}\n`, 'stderr');
    state.lastError = result.error;
    setDiagnostics(result.diagnostics || []);
    if (result.diagnostics?.length) showConsoleTab('problems');
    toast('No se pudo ejecutar', 'err');
    return;
  }
  setDiagnostics([]);
}

async function doJar() {
  const defaultName = (state.projectName || $('#main-class').value || 'app')
    .replace(/[^\w.-]+/g, '-')
    .replace(/^-|-$/g, '');
  const values = await ask({
    title: 'Generar JAR ejecutable',
    text: 'Se incluirán las librerías instaladas para que el JAR funcione con "java -jar".',
    fields: [{ name: 'name', label: 'Nombre del archivo', value: `${defaultName || 'app'}.jar` }],
    okLabel: 'Generar'
  });
  if (!values) return;

  showConsoleTab('output');
  appendConsole('\n> Generando JAR…\n', 'system');
  const result = await api.java.jar(state.files, values.name, $('#main-class').value || null, true);
  if (result.success) {
    appendConsole(`${result.message}\n${result.jarPath}\n`, 'ok');
    toast(result.message, 'ok', 6000);
    api.env.showItem(result.jarPath);
  } else {
    appendConsole(`${result.error}\n`, 'stderr');
    state.lastError = result.error;
    setDiagnostics(result.diagnostics || []);
    toast('No se pudo generar el JAR', 'err');
  }
}

// ------------------------------------------------------------------ proyectos

async function refreshProjects() {
  const { projects } = await api.projects.list();
  const list = $('#project-list');
  list.innerHTML = '';
  if (!projects.length) {
    list.innerHTML = '<li class="empty">Todavía no hay proyectos guardados</li>';
    return;
  }
  for (const project of projects) {
    const item = document.createElement('li');
    item.className = `list-item${project.name === state.projectName ? ' is-active' : ''}`;
    const when = project.updatedAt ? new Date(project.updatedAt).toLocaleDateString('es-ES') : '';
    item.innerHTML = `
      <span class="name">${escapeHtml(project.name)}</span>
      <span class="meta">${project.fileCount} · ${when}</span>
      <span class="row-actions"><button class="icon-btn" data-act="delete" title="Eliminar">🗑</button></span>`;
    item.addEventListener('click', async (event) => {
      if (event.target.dataset?.act === 'delete') {
        event.stopPropagation();
        if (!(await confirmAsk('Eliminar proyecto', `Se eliminará "${project.name}". Esta acción no se puede deshacer.`))) return;
        await api.projects.remove(project.name);
        refreshProjects();
        toast('Proyecto eliminado', 'ok');
        return;
      }
      const loaded = await api.projects.load(project.name);
      if (!loaded.success) { toast(loaded.error, 'err'); return; }
      setFiles(loaded.files, { projectName: loaded.name, chat: loaded.chatHistory || [] });
      refreshProjects();
      toast(`Proyecto "${loaded.name}" abierto`, 'ok');
    });
    list.appendChild(item);
  }
}

async function saveProject() {
  let name = state.projectName;
  if (!name) {
    const values = await ask({
      title: 'Guardar proyecto',
      fields: [{ name: 'name', label: 'Nombre del proyecto', placeholder: 'Mi proyecto Java' }],
      okLabel: 'Guardar'
    });
    if (!values || !values.name) return;
    name = values.name;
  }
  const result = await api.projects.save(name, state.files, state.chat);
  if (result.success) {
    state.projectName = result.name;
    $('#project-name').textContent = result.name;
    state.dirty.clear();
    renderTabs();
    renderFileList();
    refreshProjects();
    toast(result.message, 'ok');
  } else {
    toast(result.error, 'err');
  }
}

async function newProject() {
  if (state.dirty.size && !(await confirmAsk('Proyecto nuevo', 'Hay cambios sin guardar. ¿Quieres continuar?'))) return;
  const template = await api.templates.load('basico');
  setFiles(template.success ? template.files : { 'Main.java': 'public class Main {\n    public static void main(String[] args) {\n    }\n}\n' },
    { projectName: '', chat: [] });
  state.chat = [];
  renderChat();
  toast('Proyecto nuevo creado', 'ok');
}

// ------------------------------------------------------------------ librerías

async function refreshLibs() {
  const [{ libs }, { catalog }] = await Promise.all([api.libs.list(), api.libs.catalog()]);
  const filter = $('#lib-search').value.toLowerCase().trim();

  const installedList = $('#lib-installed');
  installedList.innerHTML = '';
  if (!libs.length) installedList.innerHTML = '<li class="empty">Sin librerías</li>';
  for (const lib of libs) {
    if (filter && !lib.name.toLowerCase().includes(filter)) continue;
    const item = document.createElement('li');
    item.className = 'list-item';
    item.innerHTML = `
      <span class="name" title="${escapeHtml(lib.path)}">${escapeHtml(lib.name)}</span>
      <span class="meta">${lib.sizeKb} KB</span>
      <span class="row-actions"><button class="icon-btn" data-act="remove" title="Eliminar">🗑</button></span>`;
    item.querySelector('[data-act="remove"]').addEventListener('click', async () => {
      const result = await api.libs.remove(lib.name);
      toast(result.success ? result.message : result.error, result.success ? 'ok' : 'err');
      refreshLibs();
    });
    installedList.appendChild(item);
  }

  const catalogList = $('#lib-catalog');
  catalogList.innerHTML = '';
  for (const entry of catalog) {
    if (entry.installed) continue;
    const haystack = `${entry.key} ${entry.artifact} ${entry.desc}`.toLowerCase();
    if (filter && !haystack.includes(filter)) continue;
    const item = document.createElement('li');
    item.className = 'list-item';
    item.innerHTML = `
      <span class="name" title="${escapeHtml(entry.group)}:${escapeHtml(entry.artifact)}:${escapeHtml(entry.version)}">
        ${escapeHtml(entry.key)} <span class="meta">${escapeHtml(entry.desc || '')}</span>
      </span>
      <span class="meta">+</span>`;
    item.addEventListener('click', async () => {
      toast(`Descargando ${entry.key}…`, 'info');
      const result = await api.libs.install(entry.key);
      toast(result.success ? result.message : result.error, result.success ? 'ok' : 'err');
      refreshLibs();
    });
    catalogList.appendChild(item);
  }
}

// ------------------------------------------------------------------ plantillas

async function refreshTemplates() {
  const { templates } = await api.templates.list();
  const list = $('#template-list');
  list.innerHTML = '';
  let category = null;
  for (const template of templates) {
    if (template.category !== category) {
      category = template.category;
      const head = document.createElement('li');
      head.className = 'subhead';
      head.textContent = category;
      list.appendChild(head);
    }
    const item = document.createElement('li');
    item.className = 'list-item';
    item.innerHTML = `
      <span class="name" title="${escapeHtml(template.description || '')}">${escapeHtml(template.name)}</span>
      <span class="meta">${template.fileCount}</span>`;
    item.addEventListener('click', async () => {
      if (state.dirty.size && !(await confirmAsk('Cargar plantilla', 'Hay cambios sin guardar. ¿Quieres continuar?'))) return;
      const loaded = await api.templates.load(template.id);
      if (!loaded.success) { toast(loaded.error, 'err'); return; }
      setFiles(loaded.files, { projectName: '', chat: [] });
      state.chat = [];
      renderChat();
      if (loaded.notes) pushChat('system', loaded.notes);
      toast(`Plantilla "${loaded.name}" cargada`, 'ok');
    });
    list.appendChild(item);
  }
}

// ------------------------------------------------------------------ asistente

function pushChat(role, content, extra = {}) {
  state.chat.push({ role, content, ...extra });
  renderChat();
}

function renderChat() {
  const container = $('#chat-messages');
  container.innerHTML = '';
  if (!state.chat.length) {
    container.innerHTML = `<div class="msg msg-system">
      <div class="msg-body"><p>Pide lo que necesites: "crea una clase que lea un CSV", "corrige este error",
      "genera los tests de TagData". El asistente ve todos los archivos del proyecto y puede crear
      o modificar archivos directamente.</p></div></div>`;
    return;
  }
  for (const message of state.chat) {
    const el = document.createElement('div');
    el.className = `msg msg-${message.role}`;
    const label = { user: 'Tú', assistant: 'Claude', system: 'Sistema', error: 'Error' }[message.role] || message.role;
    const agent = message.agent ? `<span>· ${escapeHtml(message.agent)}</span>` : '';
    const applied = (message.applied || []).map((a) =>
      `<span class="chip-file">${a.effective === 'delete' ? '−' : a.effective === 'create' ? '+' : '~'} ${escapeHtml(a.file)}</span>`
    ).join('');
    el.innerHTML = `
      <div class="msg-head"><span>${label}</span>${agent}</div>
      <div class="msg-body">${renderMarkdown(message.content)}</div>
      ${applied ? `<div class="applied">${applied}</div>` : ''}`;
    container.appendChild(el);
  }
  container.scrollTop = container.scrollHeight;
}

function showTyping(on) {
  const container = $('#chat-messages');
  const existing = $('#typing-row');
  if (!on) { existing?.remove(); return; }
  if (existing) return;
  const el = document.createElement('div');
  el.id = 'typing-row';
  el.className = 'msg msg-assistant';
  el.innerHTML = '<div class="msg-head"><span>Claude</span></div><div class="typing"><i></i><i></i><i></i></div>';
  container.appendChild(el);
  container.scrollTop = container.scrollHeight;
}

function applyAiActions(actions) {
  const applied = [];
  for (const action of actions || []) {
    if (action.type === 'create' || action.type === 'modify') {
      const existed = action.file in state.files;
      state.files[action.file] = action.content;
      const model = state.models.get(action.file);
      if (model) model.setValue(action.content);
      state.dirty.add(action.file);
      applied.push({ ...action, effective: existed ? 'modify' : 'create' });
    } else if (action.type === 'delete' && state.files[action.file]) {
      delete state.files[action.file];
      state.models.get(action.file)?.dispose();
      state.models.delete(action.file);
      applied.push({ ...action, effective: 'delete' });
    } else if (action.type === 'rename' && state.files[action.file]) {
      state.files[action.newName] = state.files[action.file];
      delete state.files[action.file];
      state.models.get(action.file)?.dispose();
      state.models.delete(action.file);
      state.dirty.add(action.newName);
      applied.push({ ...action, effective: 'rename' });
    }
  }
  if (applied.length) {
    const last = applied[applied.length - 1];
    const target = last.effective === 'rename' ? last.newName : last.file;
    if (state.files[target]) openFile(target);
    else if (Object.keys(state.files).length) openFile(Object.keys(state.files)[0]);
    renderTabs();
    renderFileList();
    refreshMainClasses();
  }
  return applied;
}

async function runAi(call, { userMessage } = {}) {
  if (state.busyAi) { toast('El asistente ya está trabajando', 'info'); return null; }
  if (!state.env?.aiReady) {
    const ready = await api.ai.available();
    if (!ready.ready) {
      toast('Añade tu API key de Anthropic en Preferencias', 'err', 5000);
      switchPanel('settings');
      return null;
    }
    state.env.aiReady = true;
  }

  if (userMessage) pushChat('user', userMessage);
  state.busyAi = true;
  $('#chat-send').disabled = true;
  $('#btn-chat-cancel').hidden = false;
  showTyping(true);

  try {
    const result = await call();
    showTyping(false);
    if (!result || result.success === false) {
      pushChat('error', result?.error || 'El asistente no ha podido responder');
      return null;
    }
    const applied = applyAiActions(result.actions);
    pushChat('assistant', result.response || '(sin respuesta)', {
      agent: result.agent,
      applied
    });
    if (result.needsContinuation) {
      pushChat('system', 'Quedan archivos por generar. Escribe "continúa" para seguir.');
    }
    return result;
  } catch (err) {
    showTyping(false);
    pushChat('error', err.message || String(err));
    return null;
  } finally {
    state.busyAi = false;
    $('#chat-send').disabled = false;
    $('#btn-chat-cancel').hidden = true;
  }
}

function chatHistoryForApi() {
  return state.chat
    .filter((m) => m.role === 'user' || m.role === 'assistant')
    .slice(-10)
    .map((m) => ({ role: m.role, content: m.content }));
}

async function sendChat(text) {
  const message = text.trim();
  if (!message) return;
  $('#chat-input').value = '';
  const history = [...chatHistoryForApi(), { role: 'user', content: message }];
  await runAi(
    () => api.ai.chat({ messages: history, files: state.files }),
    { userMessage: message }
  );
}

// ------------------------------------------------------------------ paneles

function switchPanel(name) {
  $$('.rail-btn').forEach((b) => b.classList.toggle('is-active', b.dataset.panel === name));
  $$('.panel').forEach((p) => p.classList.toggle('is-active', p.dataset.panel === name));
  if (name === 'projects') refreshProjects();
  if (name === 'libs') refreshLibs();
  if (name === 'templates') refreshTemplates();
  if (name === 'settings') loadSettingsUi();
}

// ------------------------------------------------------------------ preferencias

async function loadSettingsUi() {
  const { settings } = await api.settings.get();
  state.settings = settings;
  $('#set-apikey').placeholder = settings.hasApiKey ? settings.apiKey : 'sk-ant-…';
  $('#set-apikey').value = '';
  $('#apikey-hint').textContent = settings.hasApiKey
    ? 'Hay una clave guardada. Escribe una nueva para reemplazarla.'
    : 'Se guarda cifrada en este equipo.';
  $('#set-model').value = settings.model;
  $('#set-agents').checked = Boolean(settings.useAgents);
  $('#set-autodeps').checked = Boolean(settings.autoInstallDeps);
  $('#set-target').value = String(settings.javaTarget);
  $('#set-fontsize').value = settings.fontSize;
  $('#set-wrap').checked = Boolean(settings.wordWrap);
  await refreshJdkSelect();

  const env = state.env;
  if (env) {
    $('#about').textContent =
      `Code Weaver ${env.appVersion} · Electron ${env.electron} · Node ${env.node}\nDatos: ${env.dirs.data}`;
  }
}

async function refreshJdkSelect() {
  const { jdks } = await api.env.detectJdks();
  const select = $('#set-jdk');
  select.innerHTML = '';
  const auto = document.createElement('option');
  auto.value = '';
  auto.textContent = 'Detección automática (versión más alta)';
  select.appendChild(auto);
  for (const jdk of jdks) {
    const option = document.createElement('option');
    option.value = jdk.home;
    option.textContent = `${jdk.version} — ${jdk.home}`;
    select.appendChild(option);
  }
  select.value = state.settings.jdkHome || '';
  $('#jdk-hint').textContent = jdks.length
    ? `${jdks.length} JDK encontrado(s) en el equipo.`
    : 'No se ha encontrado ningún JDK. Instala Eclipse Temurin o elige la carpeta a mano.';
}

async function saveSetting(patch) {
  const { settings } = await api.settings.set(patch);
  state.settings = settings;
  await refreshEnv();
}

async function refreshEnv() {
  const env = await api.env.info();
  state.env = env;
  const badge = $('#jdk-badge');
  if (env.jdk) {
    badge.textContent = `JDK ${env.jdk.major} · destino ${state.settings.javaTarget || 21}`;
    badge.title = `${env.jdk.version}\n${env.jdk.home}\n(${env.jdk.source})`;
    badge.classList.remove('is-error');
  } else {
    badge.textContent = 'Sin JDK';
    badge.title = 'Instala un JDK o indícalo en Preferencias';
    badge.classList.add('is-error');
  }
}

// ------------------------------------------------------------------ arranque

async function boot() {
  const { settings } = await api.settings.get();
  state.settings = settings;

  await initEditor();
  await refreshEnv();

  const lastProject = settings.lastProject;
  let loaded = false;
  if (lastProject) {
    const project = await api.projects.load(lastProject);
    if (project.success) {
      setFiles(project.files, { projectName: project.name, chat: project.chatHistory || [] });
      loaded = true;
    }
  }
  if (!loaded) {
    const template = await api.templates.load('basico');
    setFiles(template.success ? template.files : { 'Main.java': 'public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hola");\n    }\n}\n' },
      { projectName: '', chat: [] });
  }

  renderChat();
  setDiagnostics([]);
  wireEvents();

  if (!state.env.jdk) {
    appendConsole('No se ha encontrado ningún JDK en el equipo.\nInstala un JDK (Eclipse Temurin 21) o indica su carpeta en Preferencias.\n', 'stderr');
  } else {
    appendConsole(`Code Weaver listo. ${state.env.jdk.version}\n`, 'system');
  }
}

function wireEvents() {
  // Barra superior
  $('#btn-compile').addEventListener('click', doCompile);
  $('#btn-run').addEventListener('click', doRun);
  $('#btn-stop').addEventListener('click', () => api.java.stop());
  $('#btn-jar').addEventListener('click', doJar);
  $('#btn-toggle-chat').addEventListener('click', () => $('.layout').classList.toggle('chat-hidden'));

  // Rail
  $$('.rail-btn').forEach((btn) => btn.addEventListener('click', () => switchPanel(btn.dataset.panel)));

  // Archivos
  $('#btn-new-file').addEventListener('click', newFile);
  $('#btn-import-folder').addEventListener('click', async () => {
    const result = await api.projects.importFolder();
    if (result.cancelled) return;
    if (!result.success) { toast(result.error, 'err'); return; }
    setFiles(result.files, { projectName: result.name, chat: [] });
    toast(`${Object.keys(result.files).length} archivo(s) importados`, 'ok');
  });

  // Proyectos
  $('#btn-save-project').addEventListener('click', saveProject);
  $('#btn-new-project').addEventListener('click', newProject);
  $('#btn-export-zip').addEventListener('click', async () => {
    const result = await api.projects.exportZip(state.projectName || 'proyecto', state.files);
    if (result.cancelled) return;
    toast(result.success ? result.message : result.error, result.success ? 'ok' : 'err');
  });
  $('#btn-export-folder').addEventListener('click', async () => {
    const result = await api.projects.exportFolder(state.files);
    if (result.cancelled) return;
    toast(result.success ? result.message : result.error, result.success ? 'ok' : 'err');
  });

  // Librerías
  $('#lib-search').addEventListener('input', refreshLibs);
  $('#btn-open-libs').addEventListener('click', () => api.libs.openFolder());
  $('#btn-lib-file').addEventListener('click', async () => {
    const result = await api.libs.installFromFiles();
    if (result.cancelled) return;
    toast(result.success ? `Añadidas: ${result.added.join(', ')}` : result.errors.join('; '), result.success ? 'ok' : 'err');
    refreshLibs();
  });
  $('#btn-lib-custom').addEventListener('click', async () => {
    const values = await ask({
      title: 'Instalar desde Maven Central',
      fields: [
        { name: 'group', label: 'groupId', placeholder: 'com.fazecast' },
        { name: 'artifact', label: 'artifactId', placeholder: 'jSerialComm' },
        { name: 'version', label: 'versión', placeholder: '2.10.4' }
      ],
      okLabel: 'Descargar'
    });
    if (!values) return;
    const result = await api.libs.installCustom(values);
    toast(result.success ? result.message : result.error, result.success ? 'ok' : 'err');
    refreshLibs();
  });

  // Consola
  $$('.console-tab').forEach((btn) => btn.addEventListener('click', () => showConsoleTab(btn.dataset.tab)));
  $('#btn-clear-console').addEventListener('click', () => { clearConsole(); setDiagnostics([]); });
  $('#btn-explain').addEventListener('click', async () => {
    const error = state.lastError || $('#console-output').textContent.slice(-4000);
    if (!error.trim()) { toast('No hay ningún error en la consola', 'info'); return; }
    $('.layout').classList.remove('chat-hidden');
    await runAi(
      () => api.ai.explainError({ errorMessage: error, code: state.files[state.current] || '' }),
      { userMessage: 'Explícame el error de la consola' }
    );
  });
  $('#btn-fix').addEventListener('click', async () => {
    const error = state.lastError || $('#console-output').textContent.slice(-4000);
    if (!error.trim()) { toast('No hay ningún error que corregir', 'info'); return; }
    $('.layout').classList.remove('chat-hidden');
    await runAi(
      () => api.ai.fixError({ errorMessage: error, files: state.files }),
      { userMessage: 'Corrige el error de compilación' }
    );
  });

  $('#stdin-input').addEventListener('keydown', (event) => {
    if (event.key !== 'Enter') return;
    const value = event.target.value;
    api.java.sendStdin(value);
    appendConsole(`${value}\n`, 'system');
    event.target.value = '';
  });

  // Chat
  $('#chat-form').addEventListener('submit', (event) => {
    event.preventDefault();
    sendChat($('#chat-input').value);
  });
  $('#chat-input').addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      sendChat(event.target.value);
    }
  });
  $('#btn-chat-clear').addEventListener('click', () => { state.chat = []; renderChat(); });
  $('#btn-chat-cancel').addEventListener('click', () => api.ai.cancel());

  $$('.chip[data-quick]').forEach((chip) => chip.addEventListener('click', async () => {
    const kind = chip.dataset.quick;
    if (!state.current) return;
    if (kind === 'tests') {
      await runAi(
        () => api.ai.generateTests({ className: state.current.replace(/\.java$/, ''), files: state.files }),
        { userMessage: `Genera tests JUnit 5 para ${state.current}` }
      );
    } else if (kind === 'docs') {
      await runAi(
        () => api.ai.generateDocs({ fileName: state.current, files: state.files }),
        { userMessage: `Añade JavaDoc a ${state.current}` }
      );
    } else if (kind === 'plan') {
      const values = await ask({
        title: 'Planificar proyecto',
        fields: [{ name: 'desc', label: '¿Qué quieres construir?', placeholder: 'Un lector RFID que envíe a una API…' }],
        okLabel: 'Planificar'
      });
      if (!values || !values.desc) return;
      await runAi(
        () => api.ai.plan({ description: values.desc, files: state.files }),
        { userMessage: `Planifica: ${values.desc}` }
      );
    } else if (kind === 'review') {
      await sendChat(`Revisa ${state.current} y dime qué mejorarías. No cambies nada todavía.`);
    }
  }));

  // Preferencias
  $('#set-apikey').addEventListener('change', async (event) => {
    const value = event.target.value.trim();
    if (!value) return;
    await saveSetting({ apiKey: value });
    event.target.value = '';
    state.env.aiReady = true;
    toast('API key guardada', 'ok');
    loadSettingsUi();
  });
  $('#set-model').addEventListener('change', (e) => saveSetting({ model: e.target.value }));
  $('#set-agents').addEventListener('change', (e) => saveSetting({ useAgents: e.target.checked }));
  $('#set-autodeps').addEventListener('change', (e) => saveSetting({ autoInstallDeps: e.target.checked }));
  $('#set-target').addEventListener('change', (e) => saveSetting({ javaTarget: e.target.value }));
  $('#set-jdk').addEventListener('change', (e) => saveSetting({ jdkHome: e.target.value }));
  $('#set-fontsize').addEventListener('change', (e) => {
    const size = Number(e.target.value) || 14;
    editor.updateOptions({ fontSize: size });
    saveSetting({ fontSize: size });
  });
  $('#set-wrap').addEventListener('change', (e) => {
    editor.updateOptions({ wordWrap: e.target.checked ? 'on' : 'off' });
    saveSetting({ wordWrap: e.target.checked });
  });
  $('#btn-detect-jdk').addEventListener('click', async () => { await refreshJdkSelect(); toast('Detección completada', 'ok'); });
  $('#btn-browse-jdk').addEventListener('click', async () => {
    const result = await api.env.chooseJdk();
    if (result.cancelled) return;
    if (!result.success) { toast(result.error, 'err'); return; }
    await refreshEnv();
    await loadSettingsUi();
    toast('JDK configurado', 'ok');
  });
  $('#btn-open-data').addEventListener('click', () => api.env.openPath(state.env.dirs.data));
  $('#btn-open-output').addEventListener('click', () => api.env.openPath(state.env.dirs.output));

  // Eventos del proceso principal
  api.java.onOutput(({ stream, text }) => appendConsole(text, stream));
  api.java.onExit(({ code }) => {
    setRunning(false);
    appendConsole(`\n> El proceso ha terminado con código ${code}\n`, code === 0 ? 'ok' : 'stderr');
    if (code !== 0) state.lastError = $('#console-output').textContent.slice(-4000);
  });

  api.menu.on('menu:new-project', newProject);
  api.menu.on('menu:save-project', saveProject);
  api.menu.on('menu:open-project', () => switchPanel('projects'));
  api.menu.on('menu:settings', () => switchPanel('settings'));
  api.menu.on('menu:libraries', () => switchPanel('libs'));
  api.menu.on('menu:compile', doCompile);
  api.menu.on('menu:run', doRun);
  api.menu.on('menu:stop', () => api.java.stop());
  api.menu.on('menu:jar', doJar);
  api.menu.on('menu:import-folder', () => $('#btn-import-folder').click());
  api.menu.on('menu:export-zip', () => $('#btn-export-zip').click());

  // Atajos propios del editor
  window.addEventListener('keydown', (event) => {
    if (!(event.ctrlKey || event.metaKey)) return;
    if (event.key.toLowerCase() === 's') { event.preventDefault(); saveProject(); }
  });

  window.addEventListener('beforeunload', () => {
    if (state.projectName) api.settings.set({ lastProject: state.projectName });
  });
}

boot().catch((err) => {
  document.body.innerHTML = `<pre style="padding:24px;color:#f3a5a4">No se pudo iniciar Code Weaver:\n${escapeHtml(err.stack || err.message)}</pre>`;
});
})();
