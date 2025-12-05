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
