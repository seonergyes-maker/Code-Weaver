# Code Weaver

IDE Java de escritorio (Electron) con compilador integrado, gestor de librerías Maven
y asistente Claude. Es la versión nativa del IDE que antes corría sobre Streamlit.

## Qué incluye

- **Editor** Monaco multi-archivo con pestañas, marcado de errores de `javac` en el margen
  y lista de problemas navegable.
- **Compilar / Ejecutar / Detener** con consola en streaming y entrada de teclado (stdin)
  hacia el programa Java.
- **Generar JAR** ejecutable: incorpora todas las librerías instaladas, de modo que el
  `.jar` arranca con `java -jar` sin classpath adicional.
- **Detección de JDK**: busca los JDK del equipo y usa `javac` y `java` del *mismo* JDK.
  Esto evita el clásico `UnsupportedClassVersionError` cuando en el PATH hay un JRE 8
  junto a un JDK moderno.
- **Gestor de librerías**: catálogo listo para descargar de Maven Central, instalación por
  coordenadas `groupId:artifactId:version`, `.jar` locales y detección automática de
  dependencias a partir de los `import` del código.
- **Asistente Claude** con sistema de agentes (clasificador de intención + code writer,
  error fixer, test generator, doc generator y architect). Puede crear, modificar,
  renombrar y borrar archivos del proyecto directamente.
- **Proyectos** guardados en local (sin PostgreSQL) y **plantillas**: RFID Zebra FX7500/FX9600,
  PLC Robot Fanuc y seis patrones de diseño.

## Requisitos

- Windows 10/11 x64
- Un JDK instalado (recomendado Eclipse Temurin 21). Sin JDK la app abre, pero no compila.
- Node.js 20+ solo para desarrollo o para generar el instalador.
- API key de Anthropic para el asistente (Preferencias → API key). Se guarda cifrada con
  la API de credenciales de Windows.

## Puesta en marcha

```cmd
npm install
npm start
```

## Generar el instalador

```cmd
npm run dist
```

Produce `dist\Code Weaver Setup 1.0.0.exe` (NSIS, permite elegir carpeta de instalación).

## Dónde están los datos

Todo vive en `%APPDATA%\Code Weaver`:

- `libs\` — librerías del classpath (incluye el SDK de Zebra que viaja con la app)
- `projects\` — un `.json` por proyecto guardado
- `output\` — los JAR generados
- `settings.json` — preferencias (la API key va cifrada)

Menú *Ayuda → Abrir carpeta de datos* lleva directamente ahí.

## Comprobación rápida

```cmd
npm run selftest
```

Verifica sin abrir ventana: detección de JDK, compilación multi-archivo, diagnósticos de
error, ejecución con acentos correctos, generación del fat JAR y su arranque con
`java -jar`, plantillas, proyectos y detección de dependencias.

## Ejecutar desde una unidad de red

Chromium no puede lanzar su proceso de GPU con el sandbox activo desde una unidad de red:
falla con `GPU process launch failed: error_code=18` y la app se cierra al arrancar.
`main.js` detecta que la aplicación está en una unidad mapeada o UNC y desactiva
únicamente el sandbox de la GPU; el sandbox del renderer, que es el importante, sigue
activo. Instalada en local (vía el instalador) no se aplica nada de esto.

## Notas sobre la plantilla RFID Zebra

El SDK `Symbol.RFID.API3.jar` se instala con la aplicación, pero la conexión real con el
lector necesita además la DLL nativa `RFIDAPI3_JNI_HOST.dll` del *Zebra RFID Host Java SDK
for Windows*, que debe estar en el `java.library.path`. La app añade la carpeta `libs`
de los datos de usuario a ese path, así que basta con copiar la DLL ahí.

## Estructura

```
src/main/            proceso principal de Electron
  main.js            ventana, menú y protocolo app://
  preload.js         puente seguro con el renderer (contextBridge)
  ipc.js             handlers IPC
  services/
    jdk.js           detección de JDK
    javaCompiler.js  javac / java / JAR
    dependencies.js  Maven Central y librerías locales
    projects.js      persistencia de proyectos
    templates.js     plantillas
    settings.js      preferencias (API key cifrada)
    workspace.js     carpetas de datos
  ai/
    client.js        cliente Anthropic con reintentos
    prompts.js       prompts del sistema y de cada agente
    actions.js       parseo y aplicación de acciones sobre archivos
    agents.js        coordinador y subagentes
    assistant.js     fachada del asistente
src/renderer/        interfaz (HTML + CSS + JS, Monaco)
templates/           plantillas de proyecto
bundled-libs/        librerías que viajan con la app
```
