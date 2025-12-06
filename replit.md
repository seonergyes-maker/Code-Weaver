# Java IDE con Claude AI

## Overview
Una aplicación web tipo IDE que permite desarrollar aplicaciones Java con asistencia de inteligencia artificial (Claude). Similar al estilo de Replit, permite escribir, compilar y generar archivos .jar directamente desde el navegador.

## Features
- **Editor de código Java** con resaltado de sintaxis (usando Ace Editor)
- **Compilación en tiempo real** usando OpenJDK
- **Generación de archivos .jar** descargables
- **Ejecución de código Java** con salida en consola
- **Chat con Claude AI** para:
  - Generar código nuevo basado en descripciones
  - Explicar errores de compilación
  - Sugerir mejoras al código
  - Responder preguntas sobre Java
  - **Crear y modificar archivos** automáticamente desde el chat (sin confirmación)

## Project Structure
```
├── app.py              # Aplicación principal Streamlit
├── claude_assistant.py # Integración con Claude AI (Anthropic)
├── java_compiler.py    # Funciones de compilación Java y generación JAR
├── output/             # Directorio para archivos JAR generados
└── .streamlit/
    └── config.toml     # Configuración del servidor Streamlit
```

## Technical Stack
- **Frontend**: Streamlit + streamlit-ace (editor de código)
- **AI**: Claude API via Replit AI Integrations (Anthropic)
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
- Las respuestas de "acciones rápidas" de IA se separan del historial de chat para cumplir con la API de Anthropic

## Recent Changes
- Diciembre 2025: Creación inicial del proyecto
  - Implementación del editor de código con Ace
  - Integración con Claude AI para asistencia
  - Sistema de compilación y generación de JAR
  - Interfaz de chat interactivo
  - Límites de memoria y tiempo para ejecución Java
  - Separación de notificaciones AI del historial de chat
  - Soporte multi-archivo para proyectos Java
  - Persistencia de proyectos en base de datos PostgreSQL
  - Autocompletado de línea inteligente con Claude AI
  - Gestor de dependencias para librerías externas (Maven Central)
  - Soporte para librerías comunes: Gson, Guava, Jackson, OkHttp, etc.
  - Importación de proyectos existentes (.java y .zip)
  - **NUEVO: Contexto completo del proyecto** - Claude ahora recibe TODOS los archivos del proyecto en cada conversación
  - **NUEVO: Bucle automático multi-archivo** - Cuando Claude indica que hay más archivos por crear, el sistema continúa automáticamente hasta 10 archivos sin intervención del usuario
  - **NUEVO: Acciones delete y rename** - Claude puede eliminar y renombrar archivos además de crearlos y modificarlos
  - **NUEVO: Auto-corrección de errores** - Cuando hay un error de compilación, Claude automáticamente analiza y corrige el código (toggle en la barra lateral)
  - **NUEVO: Historial de chat persistente** - El historial de conversación se guarda con cada proyecto en la base de datos
  - **FASE 2: Modo Arquitecto** - Analiza requisitos y crea un plan de implementación antes de escribir código
  - **FASE 2: Análisis de Ejecución** - Interpreta la salida del programa y sugiere mejoras
  - **FASE 2: Búsqueda en Proyecto** - Encuentra código, clases, métodos y patrones en el proyecto
  - **FASE 3: Generación de Tests JUnit** - Crea tests unitarios automáticamente para clases Java
  - **FASE 3: Documentación Automática** - Genera JavaDoc profesional para métodos y clases
  - **FASE 3: Plantillas de Código** - Templates para patrones de diseño (Singleton, Factory, Observer, MVC, Builder, Strategy)
  - **FASE 3: Exportar Proyecto** - Descarga todo el proyecto como .zip con archivos, librerías y README
