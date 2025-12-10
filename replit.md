# Java IDE - RFID ZEBRA FX7500

## Overview
Una aplicación web tipo IDE que permite desarrollar aplicaciones Java para lectores RFID Zebra FX7500/FX9600 usando el **SDK oficial de Zebra**.

## Proyecto Actual: RFID ZEBRA FX7500 V1.2
- Usa el **SDK oficial de Zebra** (Symbol.RFID.API3.jar)
- Requiere DLL nativa `RFIDAPI3_JNI_HOST.dll` en Windows
- Lectura continua de tags en tiempo real
- Muestra EPC, RSSI, antena y contador de lecturas

## SDK de Zebra - Requisitos
1. Descargar SDK desde: https://www.zebra.com/us/en/support-downloads/software/rfid-software/rfid-java-sdk.html
2. Instalar "Zebra RFID Host Java SDK for Windows v1.6"
3. Archivos necesarios:
   - `Symbol.RFID.API3.jar` - Librería Java
   - `RFIDAPI3_JNI_HOST.dll` - DLL nativa JNI

## Ejecución en Windows
```cmd
cd "C:\Program Files\Zebra Technologies\RFID Host Java SDK\lib"
java -Djava.library.path=. -cp "ZebraRFIDReader.jar;Symbol.RFID.API3.jar" ZebraRFIDReader 192.168.1.117
```

## Project Structure
```
├── app.py              # Aplicación principal Streamlit (IDE)
├── java_compiler.py    # Funciones de compilación Java
├── database.py         # Persistencia de proyectos en PostgreSQL
├── java_sources/       # Código fuente Java
│   └── ZebraRFIDReader.java  # Lector RFID con SDK Zebra
├── libs/               # Librerías externas
│   └── Symbol.RFID.API3.jar  # SDK oficial de Zebra
└── output/             # Archivos JAR generados
```

## Technical Stack
- **Frontend**: Streamlit + streamlit-ace (editor de código)
- **Compilador**: OpenJDK (javac, jar)
- **SDK RFID**: Zebra Symbol.RFID.API3 (com.mot.rfid.api3)
- **Lenguaje**: Python 3.11

## User Preferences
- Interfaz en español
- Código Java bien comentado
- Mensajes de error claros y explicativos

## Recent Changes
- Diciembre 2025:
  - **V1.2**: Migración completa a SDK oficial de Zebra
  - Eliminado protocolo LLRP4J (reemplazado por SDK nativo)
  - Proyecto limpio solo con código del SDK
  - Lectura en tiempo real con eventos (RfidEventsListener)
