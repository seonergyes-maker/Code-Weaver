# Java IDE - RFID ZEBRA FX7500

## Overview
Una aplicación web tipo IDE que permite desarrollar aplicaciones Java para lectores RFID Zebra FX7500/FX9600 usando el **SDK oficial de Zebra**.

## Proyecto Actual: RFID ZEBRA FX7500 PLC V1.4
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

## Integración PLC (Modbus TCP)
El sistema incluye comunicación con PLCs industriales via Modbus TCP:

**Flujo de operación:**
1. PLC activa coil enabler → App detecta activación
2. App lee último tag RFID leído
3. App consulta API para obtener datos del producto (tipo_embalaje, ancho, largo)
4. App escribe valores a registros holding del PLC
5. App activa registro de confirmación

**Configuración PLC:**
- IP y puerto del PLC (default 502)
- Intervalo de polling (default 500ms)
- Referencia del coil enabler + Unit ID
- Referencias de registros: tipo_embalaje, ancho, largo, activa

**Archivos relacionados:**
- `ModbusClient.java` - Cliente Modbus TCP
- Pestaña "PLC" en RFIDMainWindow

## Proyecto: PLC ROBOT FANUC v1.0.2 by Daemon4
Aplicación Java para monitoreo de API JSON y control de PLC via Modbus TCP.

**Flujo de operación:**
1. Consulta periódica a API: `{endpoint}/rfid_lecturas/apilado`
2. Cuando Coil Enabler cambia 0→1: Escribe datos del primer item a Holding Registers (TipoEmbalaje, Ancho, Largo, Pila, Control)
3. Cuando Coil cambia 1→0: Llama API de baja (GET): `{endpoint}/rfid_lecturas/baja_apilado/{token}/{ip}/{trabajo}/{tag}/{numero}`

**Archivos del proyecto:**
- `Main.java` - Punto de entrada
- `PLCRobotFanucWindow.java` - Ventana principal (4 pestañas: Monitoreo, API, PLC, Opciones)
- `PLCRobotFanucConfig.java` - Configuración en formato INI (config.ini)
- `RobotFanucAPIClient.java` - Cliente API con polling
- `RobotFanucModbusClient.java` - Cliente Modbus TCP
- `LogManager.java` - Sistema de logs separados (tags y errores)
- `ModernUIStyle.java` - Estilos UI tema claro (fondo blanco)

**Registros Holding por defecto:**
- HR 0 = TipoEmbalaje
- HR 1 = Ancho
- HR 2 = Largo
- HR 3 = Pila
- HR 4 = Control

**Características:**
- Configuración persistente en `config.ini` (formato INI)
- Logs diarios en carpeta `logs/`:
  - `tags_YYYYMMDD.log` - Tags gestionados
  - `errores_YYYYMMDD.log` - Errores del sistema
- System Tray de Windows (minimizar a bandeja)
- Pestaña Opciones con:
  - Inicio automático de API/PLC
  - Minimizar al iniciar
  - Generador de script para servicio Windows (NSSM)

**Ejecución:**
```cmd
java -jar PLCRobotFanuc.jar
```

**Directorio de salida:** `output_plc_robot_fanuc/`

## Recent Changes
- Diciembre 2025:
  - **PLC ROBOT FANUC v1.0.2**: Renombrado de PLC COGER, añadido campo "pila"
    - Interfaz gráfica con tema claro (fondo blanco, letras oscuras, sin emojis)
    - 4 pestañas: Monitoreo, API, PLC, Opciones
    - Polling configurable de API JSON
    - Comunicación Modbus TCP con PLCs
    - Transiciones de coil: 0→1 escribe datos, 1→0 llama baja (GET)
    - Configuración en formato INI local (config.ini)
    - Logs separados: tags_YYYYMMDD.log y errores_YYYYMMDD.log
    - System Tray de Windows (minimizar a bandeja)
    - Opciones de inicio automático (API y PLC)
    - Generador de script para servicio Windows (NSSM)
  - **V1.3+**: Opciones "Apilado" y "Borrar cola al parar" en RFID
  - **V1.3**: Integración PLC con Modbus TCP
    - Nueva pestaña PLC con configuración completa
    - ModbusClient para comunicación industrial
    - Polling automático del coil enabler
    - Escritura de datos de producto a registros holding
  - **V1.2**: Migración completa a SDK oficial de Zebra
  - Eliminado protocolo LLRP4J (reemplazado por SDK nativo)
  - Proyecto limpio solo con código del SDK
  - Lectura en tiempo real con eventos (RfidEventsListener)
