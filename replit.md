# Java IDE

## Overview
Una aplicación web tipo IDE que permite desarrollar aplicaciones Java. Similar al estilo de Replit, permite escribir, compilar y generar archivos .jar directamente desde el navegador.

## Features
- **Editor de código Java** con resaltado de sintaxis (usando Ace Editor)
- **Compilación en tiempo real** usando OpenJDK
- **Generación de archivos .jar** descargables
- **Ejecución de código Java** con salida en consola
- **Gestión de proyectos** con persistencia en base de datos
- **Librerías externas** desde Maven Central
- **Exportación de proyectos** como .zip
- **Scripts de despliegue** para Ubuntu/Debian con systemd

## Project Structure
```
├── app.py              # Aplicación principal Streamlit
├── java_compiler.py    # Funciones de compilación Java y generación JAR
├── database.py         # Persistencia de proyectos en PostgreSQL
├── dependency_manager.py # Gestor de librerías externas (Maven Central)
├── output/             # Directorio para archivos JAR generados
└── .streamlit/
    └── config.toml     # Configuración del servidor Streamlit
```

## Technical Stack
- **Frontend**: Streamlit + streamlit-ace (editor de código)
- **Compilador**: OpenJDK (javac, jar)
- **Lenguaje**: Python 3.11

## Running the App
```bash
streamlit run app.py --server.port 5000
```

## User Preferences
- Interfaz en español
- Código Java bien comentado
- Mensajes de error claros y explicativos

## Security Considerations
- Ejecución Java tiene límites de memoria (128MB max) y tiempo (30 segundos)
- Este IDE está diseñado para uso personal en entornos Replit
- Para despliegue público multi-usuario, se requeriría sandboxing adicional (Docker, Firejail)

## Recent Changes
- Diciembre 2025: Creación inicial del proyecto
  - Implementación del editor de código con Ace
  - Sistema de compilación y generación de JAR
  - Límites de memoria y tiempo para ejecución Java
  - Soporte multi-archivo para proyectos Java
  - Persistencia de proyectos en base de datos PostgreSQL
  - Gestor de dependencias para librerías externas (Maven Central)
  - Soporte para librerías comunes: Gson, Guava, Jackson, OkHttp, etc.
  - Importación de proyectos existentes (.java y .zip)
  - Exportar Proyecto - Descarga todo el proyecto como .zip
  - Fat JAR - Los archivos JAR incluyen automáticamente todas las librerías
  - Script Ubuntu - Script de instalación para Ubuntu/Debian con systemd
  - Instalación Automática de Librerías - Detecta imports y descarga las librerías
  - **ELIMINADAS TODAS LAS FUNCIONES DE IA** - El IDE ahora funciona sin Claude ni ninguna IA
