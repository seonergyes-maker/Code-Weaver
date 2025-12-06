import streamlit as st
from streamlit_ace import st_ace
import os
import zipfile
import io

from java_compiler import (
    compile_java, create_jar, run_java, get_java_version,
    compile_multi_file, run_multi_file, create_multi_jar, find_class_name
)
from claude_assistant import (
    chat_with_claude, 
    generate_java_code, 
    explain_error, 
    suggest_improvements,
    complete_line,
    chat_with_actions,
    auto_fix_error,
    architect_analyze,
    analyze_execution,
    search_in_project,
    generate_junit_tests,
    generate_javadoc,
    get_code_template,
    get_available_templates
)
from database import save_project, load_project, list_projects, delete_project, update_chat_history, load_project_by_name
from dependency_manager import (
    download_library, download_custom_library, list_installed_libraries,
    remove_library, get_available_libraries, COMMON_LIBRARIES,
    install_detected_dependencies
)

st.set_page_config(
    page_title="Java IDE con Claude AI",
    page_icon="☕",
    layout="wide",
    initial_sidebar_state="expanded"
)

st.markdown("""
<style>
    .main-header {
        font-size: 2rem;
        font-weight: bold;
        color: #E57373;
        margin-bottom: 0.5rem;
    }
    .sub-header {
        color: #888;
        margin-bottom: 1rem;
    }
    .stButton > button {
        width: 100%;
    }
    .file-tab {
        padding: 0.5rem 1rem;
        border-radius: 0.25rem 0.25rem 0 0;
        cursor: pointer;
    }
    .file-tab-active {
        background-color: #2D2D2D;
        color: white;
    }
</style>
""", unsafe_allow_html=True)

DEFAULT_FILES = {
    "Main.java": '''public class Main {
    public static void main(String[] args) {
        System.out.println("Hola desde Java IDE!");
        
        // Ejemplo usando otra clase
        Calculadora calc = new Calculadora();
        System.out.println("5 + 3 = " + calc.sumar(5, 3));
    }
}''',
    "Calculadora.java": '''public class Calculadora {
    public int sumar(int a, int b) {
        return a + b;
    }
    
    public int restar(int a, int b) {
        return a - b;
    }
    
    public int multiplicar(int a, int b) {
        return a * b;
    }
    
    public double dividir(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("No se puede dividir por cero");
        }
        return (double) a / b;
    }
}'''
}

if 'files' not in st.session_state:
    st.session_state.files = DEFAULT_FILES.copy()
if 'current_file' not in st.session_state:
    st.session_state.current_file = list(DEFAULT_FILES.keys())[0]
if 'code' not in st.session_state:
    st.session_state.code = st.session_state.files[st.session_state.current_file]
if 'console_output' not in st.session_state:
    st.session_state.console_output = ""
if 'chat_messages' not in st.session_state:
    st.session_state.chat_messages = []
if 'jar_files' not in st.session_state:
    st.session_state.jar_files = []
if 'ai_notifications' not in st.session_state:
    st.session_state.ai_notifications = []
if 'new_file_name' not in st.session_state:
    st.session_state.new_file_name = ""
if 'current_project_name' not in st.session_state:
    st.session_state.current_project_name = "Proyecto Sin Guardar"
if 'saved_projects' not in st.session_state:
    st.session_state.saved_projects = list_projects()
if 'pending_actions' not in st.session_state:
    st.session_state.pending_actions = []
if 'auto_fix_enabled' not in st.session_state:
    st.session_state.auto_fix_enabled = True
if 'last_error' not in st.session_state:
    st.session_state.last_error = None
if 'show_architect_modal' not in st.session_state:
    st.session_state.show_architect_modal = False
if 'architect_plan' not in st.session_state:
    st.session_state.architect_plan = None
if 'show_templates_modal' not in st.session_state:
    st.session_state.show_templates_modal = False

def get_all_code():
    return "\n\n// --- Archivo: ".join([f"{name} ---\n{code}" for name, code in st.session_state.files.items()])

with st.sidebar:
    st.markdown("### Asistente Claude AI")
    st.markdown("---")
    
    st.markdown("**Acciones Rápidas**")
    
    if st.button("Generar Código", use_container_width=True):
        st.session_state.show_generate_modal = True
    
    if st.button("Sugerir Mejoras", use_container_width=True):
        current_code = st.session_state.files.get(st.session_state.current_file, "")
        if current_code.strip():
            with st.spinner("Analizando código..."):
                try:
                    suggestions = suggest_improvements(current_code)
                    st.session_state.ai_notifications.append({
                        "type": "suggestions",
                        "content": f"**Sugerencias para {st.session_state.current_file}:**\n\n{suggestions}"
                    })
                except Exception as e:
                    st.error(f"Error: {e}")
        else:
            st.warning("Escribe código primero")
    
    if st.button("Explicar Último Error", use_container_width=True):
        if "error" in st.session_state.console_output.lower() or "exception" in st.session_state.console_output.lower():
            with st.spinner("Analizando error..."):
                try:
                    explanation = explain_error(st.session_state.console_output, get_all_code())
                    st.session_state.ai_notifications.append({
                        "type": "error_explanation",
                        "content": f"**Explicación del error:**\n\n{explanation}"
                    })
                except Exception as e:
                    st.error(f"Error: {e}")
        else:
            st.info("No hay errores recientes para explicar")
    
    if st.button("Autocompletar Línea", use_container_width=True):
        current_code = st.session_state.files.get(st.session_state.current_file, "")
        if current_code.strip():
            lines = current_code.split('\n')
            last_line = lines[-1] if lines else ""
            if last_line.strip():
                with st.spinner("Autocompletando..."):
                    try:
                        completion = complete_line(current_code, last_line)
                        if completion:
                            st.session_state.ai_notifications.append({
                                "type": "autocomplete",
                                "content": f"**Sugerencia de autocompletado:**\n\n`{last_line}` → `{last_line}{completion}`\n\nCopia y pega la sugerencia en tu código."
                            })
                        else:
                            st.info("La línea parece estar completa")
                    except Exception as e:
                        st.error(f"Error: {e}")
            else:
                st.info("Escribe algo en la última línea primero")
        else:
            st.warning("Escribe código primero")
    
    st.markdown("---")
    st.markdown("**Herramientas Avanzadas**")
    
    if st.button("🏗️ Modo Arquitecto", use_container_width=True, help="Analiza y planifica antes de implementar"):
        st.session_state.show_architect_modal = True
    
    if st.button("🔍 Analizar Ejecución", use_container_width=True, help="Interpreta la salida del programa"):
        if st.session_state.console_output.strip():
            with st.spinner("Analizando salida..."):
                try:
                    is_error = "error" in st.session_state.console_output.lower() or "exception" in st.session_state.console_output.lower()
                    analysis = analyze_execution(
                        st.session_state.console_output, 
                        st.session_state.files,
                        is_error
                    )
                    st.session_state.ai_notifications.append({
                        "type": "execution_analysis",
                        "content": f"**Análisis de ejecución:**\n\n{analysis}"
                    })
                except Exception as e:
                    st.error(f"Error: {e}")
        else:
            st.info("Ejecuta el programa primero para analizar la salida")
    
    search_col1, search_col2 = st.columns([3, 1])
    with search_col1:
        search_query = st.text_input("🔎 Buscar:", placeholder="clase, método...", key="search_input", label_visibility="collapsed")
    with search_col2:
        do_search = st.button("🔍", key="search_btn", use_container_width=True)
    
    if do_search and search_query:
        with st.spinner("Buscando..."):
            try:
                results = search_in_project(search_query, st.session_state.files)
                if results.get("results"):
                    result_text = f"**Resultados para '{search_query}':**\n\n"
                    for r in results["results"]:
                        result_text += f"📍 **{r.get('file', 'N/A')}** (línea {r.get('line', 'N/A')})\n"
                        result_text += f"   `{r.get('match', '')}`\n"
                        result_text += f"   _{r.get('relevance', '')}_\n\n"
                    result_text += f"\n**Resumen:** {results.get('summary', '')}"
                else:
                    result_text = f"**Búsqueda:** '{search_query}'\n\n{results.get('summary', 'No se encontraron resultados')}"
                st.session_state.ai_notifications.append({
                    "type": "search",
                    "content": result_text
                })
                st.rerun()
            except Exception as e:
                st.error(f"Error: {e}")
    
    st.markdown("---")
    st.session_state.auto_fix_enabled = st.toggle(
        "🔧 Auto-corrección de errores",
        value=st.session_state.auto_fix_enabled,
        help="Cuando está activo, Claude analizará y corregirá automáticamente los errores de compilación"
    )
    
    st.markdown("---")
    st.markdown("**Herramientas de Desarrollo**")
    
    if st.button("🧪 Generar Tests JUnit", use_container_width=True, help="Crea tests unitarios para la clase actual"):
        current_code = st.session_state.files.get(st.session_state.current_file, "")
        if current_code.strip():
            class_name = st.session_state.current_file.replace('.java', '')
            with st.spinner("Generando tests..."):
                try:
                    test_code = generate_junit_tests(current_code, class_name)
                    if not test_code or len(test_code) < 50:
                        st.error("No se pudo generar código de test válido. Intenta de nuevo.")
                    elif "class" not in test_code:
                        st.error("El test generado no contiene una definición de clase válida. Intenta de nuevo.")
                    elif "@Test" not in test_code and "org.junit" not in test_code:
                        st.error("El test generado no contiene anotaciones JUnit. Intenta de nuevo.")
                    else:
                        test_filename = f"{class_name}Test.java"
                        st.session_state.files[test_filename] = test_code
                        st.session_state.ai_notifications.append({
                            "type": "generation",
                            "content": f"**Tests generados:** `{test_filename}`\n\nSe crearon tests JUnit 5 para la clase `{class_name}`. Necesitarás JUnit 5 en tu classpath para ejecutarlos."
                        })
                        st.rerun()
                except Exception as e:
                    st.error(f"Error: {e}")
        else:
            st.warning("Escribe código primero")
    
    if st.button("📚 Generar JavaDoc", use_container_width=True, help="Añade documentación profesional al código"):
        current_code = st.session_state.files.get(st.session_state.current_file, "")
        if current_code.strip():
            with st.spinner("Generando documentación..."):
                try:
                    documented_code = generate_javadoc(current_code)
                    if not documented_code or len(documented_code) < len(current_code) * 0.5:
                        st.error("Error al generar documentación. El código original no fue modificado.")
                    elif "class" not in documented_code:
                        st.error("La documentación generada parece inválida. El código original no fue modificado.")
                    else:
                        backup_filename = f"{st.session_state.current_file}.backup"
                        st.session_state.files[backup_filename] = current_code
                        st.session_state.files[st.session_state.current_file] = documented_code
                        st.session_state.code = documented_code
                        st.session_state.ai_notifications.append({
                            "type": "documentation",
                            "content": f"**JavaDoc añadido a** `{st.session_state.current_file}`\n\nSe agregó documentación completa. Se creó un backup en `{backup_filename}` por seguridad."
                        })
                        st.rerun()
                except Exception as e:
                    st.error(f"Error: {e}")
        else:
            st.warning("Escribe código primero")
    
    if st.button("📋 Plantillas de Código", use_container_width=True, help="Patrones de diseño listos para usar"):
        st.session_state.show_templates_modal = True
    
    if st.button("📦 Exportar Proyecto", use_container_width=True, help="Descarga todo el proyecto como .zip"):
        if st.session_state.files:
            zip_buffer = io.BytesIO()
            with zipfile.ZipFile(zip_buffer, 'w', zipfile.ZIP_DEFLATED) as zip_file:
                for filename, content in st.session_state.files.items():
                    zip_file.writestr(filename, content)
                
                libs_path = "libs"
                if os.path.exists(libs_path):
                    for lib_file in os.listdir(libs_path):
                        if lib_file.endswith('.jar'):
                            lib_path = os.path.join(libs_path, lib_file)
                            zip_file.write(lib_path, f"libs/{lib_file}")
                
                project_name = st.session_state.get('current_project_name', 'JavaProject').replace(" ", "_")
                readme_content = f"""# {project_name}

## Archivos Java
{chr(10).join([f"- {f}" for f in st.session_state.files.keys()])}

## Compilación
javac *.java

## Ejecución
java Main

Generado por Java IDE con Claude AI
"""
                zip_file.writestr("README.md", readme_content)
            
            zip_buffer.seek(0)
            st.download_button(
                label="⬇️ Descargar ZIP",
                data=zip_buffer.getvalue(),
                file_name=f"{project_name}.zip",
                mime="application/zip",
                use_container_width=True
            )
        else:
            st.warning("No hay archivos para exportar")
    
    if st.button("🐧 Script Ubuntu", use_container_width=True, help="Genera script de instalación para Ubuntu/Debian"):
        if st.session_state.files:
            from java_compiler import find_main_class
            project_name = st.session_state.get('current_project_name', 'JavaProject').replace(" ", "_").lower()
            main_class = find_main_class(st.session_state.files) or "Main"
            jar_name = f"{main_class}.jar"
            
            libs_installed = []
            if os.path.exists("libs"):
                libs_installed = [f for f in os.listdir("libs") if f.endswith('.jar')]
            
            deploy_script = f'''#!/bin/bash
# =============================================================================
# Script de Instalación AUTOMÁTICA para Ubuntu/Debian
# Proyecto: {project_name}
# Generado por Java IDE con Claude AI
# 
# USO: chmod +x install.sh && sudo ./install.sh
# OPCIONES:
#   --no-service    No crear servicio systemd
#   --no-start      No iniciar automáticamente
#   --uninstall     Desinstalar la aplicación
# =============================================================================

set -e

# Colores
RED='\\033[0;31m'
GREEN='\\033[0;32m'
YELLOW='\\033[1;33m'
BLUE='\\033[0;34m'
NC='\\033[0m'

APP_NAME="{project_name}"
APP_DIR="/opt/$APP_NAME"
JAR_NAME="{jar_name}"
SERVICE_NAME="$APP_NAME"
CREATE_SERVICE=true
AUTO_START=true

# Procesar argumentos
for arg in "$@"; do
    case $arg in
        --no-service) CREATE_SERVICE=false ;;
        --no-start) AUTO_START=false ;;
        --uninstall)
            echo -e "${{YELLOW}}Desinstalando $APP_NAME...${{NC}}"
            sudo systemctl stop $SERVICE_NAME 2>/dev/null || true
            sudo systemctl disable $SERVICE_NAME 2>/dev/null || true
            sudo rm -f /etc/systemd/system/$SERVICE_NAME.service
            sudo rm -f /usr/local/bin/$APP_NAME
            sudo rm -rf $APP_DIR
            sudo systemctl daemon-reload
            echo -e "${{GREEN}}¡Desinstalación completada!${{NC}}"
            exit 0
            ;;
    esac
done

echo -e "${{BLUE}}╔══════════════════════════════════════════════════════════════╗${{NC}}"
echo -e "${{BLUE}}║${{NC}}  ${{GREEN}}Instalador Automático de $APP_NAME${{NC}}"
echo -e "${{BLUE}}║${{NC}}  Generado por Java IDE con Claude AI"
echo -e "${{BLUE}}╚══════════════════════════════════════════════════════════════╝${{NC}}"

SCRIPT_DIR="$(cd "$(dirname "${{BASH_SOURCE[0]}}")" && pwd)"

# ============================================================================
# PASO 1: Verificar permisos root
# ============================================================================
echo -e "\\n${{YELLOW}}[1/7] Verificando permisos...${{NC}}"
if [ "$EUID" -ne 0 ]; then
    echo -e "${{RED}}Este script necesita ejecutarse como root${{NC}}"
    echo "Uso: sudo ./install.sh"
    exit 1
fi
echo -e "${{GREEN}}✓ Ejecutando como root${{NC}}"

# ============================================================================
# PASO 2: Actualizar sistema e instalar dependencias
# ============================================================================
echo -e "\\n${{YELLOW}}[2/7] Actualizando sistema...${{NC}}"
apt-get update -qq
apt-get install -y -qq curl wget unzip > /dev/null
echo -e "${{GREEN}}✓ Sistema actualizado${{NC}}"

# ============================================================================
# PASO 3: Instalar Java (OpenJDK 17)
# ============================================================================
echo -e "\\n${{YELLOW}}[3/7] Configurando Java...${{NC}}"
if ! command -v java &> /dev/null; then
    echo "Instalando OpenJDK 17..."
    apt-get install -y openjdk-17-jre-headless > /dev/null
    echo -e "${{GREEN}}✓ Java 17 instalado${{NC}}"
else
    java_version=$(java -version 2>&1 | head -n 1)
    echo -e "${{GREEN}}✓ Java detectado: $java_version${{NC}}"
fi

# Verificar JAVA_HOME
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
    echo "export JAVA_HOME=$JAVA_HOME" >> /etc/environment
fi

# ============================================================================
# PASO 4: Crear estructura de directorios
# ============================================================================
echo -e "\\n${{YELLOW}}[4/7] Creando estructura de directorios...${{NC}}"
mkdir -p "$APP_DIR"
mkdir -p "$APP_DIR/logs"
mkdir -p "$APP_DIR/config"
mkdir -p "$APP_DIR/libs"
echo -e "${{GREEN}}✓ Directorios creados en $APP_DIR${{NC}}"

# ============================================================================
# PASO 5: Copiar archivos de la aplicación
# ============================================================================
echo -e "\\n${{YELLOW}}[5/7] Copiando archivos...${{NC}}"

# Buscar y copiar JAR
JAR_FOUND=false
for search_path in "$SCRIPT_DIR/$JAR_NAME" "$SCRIPT_DIR/output/$JAR_NAME" "$SCRIPT_DIR/*.jar"; do
    if [ -f "$search_path" ] 2>/dev/null || ls $search_path 1>/dev/null 2>&1; then
        if [ -f "$search_path" ]; then
            cp "$search_path" "$APP_DIR/$JAR_NAME"
        else
            cp $search_path "$APP_DIR/" 2>/dev/null || true
        fi
        JAR_FOUND=true
        break
    fi
done

if [ "$JAR_FOUND" = false ]; then
    echo -e "${{RED}}✗ No se encontró el archivo JAR${{NC}}"
    echo "  Coloca $JAR_NAME junto a este script e intenta de nuevo"
    exit 1
fi
echo -e "${{GREEN}}✓ JAR copiado${{NC}}"

# Copiar librerías si existen
if [ -d "$SCRIPT_DIR/libs" ]; then
    cp -r "$SCRIPT_DIR/libs/"*.jar "$APP_DIR/libs/" 2>/dev/null || true
    LIB_COUNT=$(ls -1 "$APP_DIR/libs/"*.jar 2>/dev/null | wc -l)
    echo -e "${{GREEN}}✓ $LIB_COUNT librerías copiadas${{NC}}"
fi

# Copiar archivos de configuración si existen
if [ -d "$SCRIPT_DIR/config" ]; then
    cp -r "$SCRIPT_DIR/config/"* "$APP_DIR/config/" 2>/dev/null || true
fi

# ============================================================================
# PASO 6: Crear scripts de ejecución
# ============================================================================
echo -e "\\n${{YELLOW}}[6/7] Configurando scripts de ejecución...${{NC}}"

# Script principal de ejecución
cat > "$APP_DIR/run.sh" << 'RUNSCRIPT'
#!/bin/bash
APP_DIR="/opt/{project_name}"
JAR="$APP_DIR/{jar_name}"
LOG_DIR="$APP_DIR/logs"
JAVA_OPTS="-Xmx512m -Xms128m"

cd "$APP_DIR"

# Construir classpath con librerías
CLASSPATH="$JAR"
if [ -d "$APP_DIR/libs" ]; then
    for lib in "$APP_DIR/libs/"*.jar; do
        [ -f "$lib" ] && CLASSPATH="$CLASSPATH:$lib"
    done
fi

exec java $JAVA_OPTS -cp "$CLASSPATH" -jar "$JAR" "$@"
RUNSCRIPT
chmod +x "$APP_DIR/run.sh"

# Script de control
cat > "$APP_DIR/control.sh" << 'CONTROLSCRIPT'
#!/bin/bash
APP="{project_name}"

case "$1" in
    start)
        sudo systemctl start $APP
        echo "Iniciado $APP"
        ;;
    stop)
        sudo systemctl stop $APP
        echo "Detenido $APP"
        ;;
    restart)
        sudo systemctl restart $APP
        echo "Reiniciado $APP"
        ;;
    status)
        sudo systemctl status $APP
        ;;
    logs)
        journalctl -u $APP -f
        ;;
    *)
        echo "Uso: $0 {{start|stop|restart|status|logs}}"
        exit 1
        ;;
esac
CONTROLSCRIPT
chmod +x "$APP_DIR/control.sh"

# Enlace simbólico global
ln -sf "$APP_DIR/run.sh" "/usr/local/bin/$APP_NAME"
echo -e "${{GREEN}}✓ Comando '$APP_NAME' disponible globalmente${{NC}}"

# ============================================================================
# PASO 7: Crear servicio systemd
# ============================================================================
if [ "$CREATE_SERVICE" = true ]; then
    echo -e "\\n${{YELLOW}}[7/7] Configurando servicio systemd...${{NC}}"
    
    cat > /etc/systemd/system/$SERVICE_NAME.service << SERVICEEOF
[Unit]
Description={project_name} Java Application
Documentation=https://github.com/user/{project_name}
After=network.target
Wants=network-online.target

[Service]
Type=simple
User=root
Group=root
WorkingDirectory=$APP_DIR
ExecStart=/usr/bin/java -Xmx512m -jar $APP_DIR/$JAR_NAME
ExecStop=/bin/kill -TERM \$MAINPID
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=$APP_NAME

# Seguridad
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=$APP_DIR/logs $APP_DIR/config

[Install]
WantedBy=multi-user.target
SERVICEEOF

    systemctl daemon-reload
    systemctl enable $SERVICE_NAME > /dev/null 2>&1
    echo -e "${{GREEN}}✓ Servicio systemd configurado${{NC}}"
    
    if [ "$AUTO_START" = true ]; then
        systemctl start $SERVICE_NAME
        echo -e "${{GREEN}}✓ Servicio iniciado automáticamente${{NC}}"
    fi
else
    echo -e "\\n${{YELLOW}}[7/7] Servicio systemd omitido (--no-service)${{NC}}"
fi

# ============================================================================
# RESUMEN FINAL
# ============================================================================
echo ""
echo -e "${{BLUE}}╔══════════════════════════════════════════════════════════════╗${{NC}}"
echo -e "${{BLUE}}║${{NC}}  ${{GREEN}}¡INSTALACIÓN COMPLETADA EXITOSAMENTE!${{NC}}"
echo -e "${{BLUE}}╚══════════════════════════════════════════════════════════════╝${{NC}}"
echo ""
echo -e "${{GREEN}}Directorio de instalación:${{NC}} $APP_DIR"
echo ""
echo -e "${{YELLOW}}Comandos disponibles:${{NC}}"
echo "  $APP_NAME              - Ejecutar aplicación"
echo "  $APP_NAME --help       - Ver ayuda (si está implementada)"
echo ""
if [ "$CREATE_SERVICE" = true ]; then
    echo -e "${{YELLOW}}Control del servicio:${{NC}}"
    echo "  sudo systemctl start $SERVICE_NAME     - Iniciar"
    echo "  sudo systemctl stop $SERVICE_NAME      - Detener"
    echo "  sudo systemctl restart $SERVICE_NAME   - Reiniciar"
    echo "  sudo systemctl status $SERVICE_NAME    - Ver estado"
    echo "  journalctl -u $SERVICE_NAME -f         - Ver logs en vivo"
    echo ""
    
    # Mostrar estado actual
    if systemctl is-active --quiet $SERVICE_NAME; then
        echo -e "${{GREEN}}Estado actual: ● EJECUTÁNDOSE${{NC}}"
    else
        echo -e "${{YELLOW}}Estado actual: ○ Detenido${{NC}}"
    fi
fi
echo ""
echo -e "${{YELLOW}}Para desinstalar:${{NC}} sudo ./install.sh --uninstall"
echo ""
'''
            
            st.download_button(
                label="⬇️ Descargar install.sh",
                data=deploy_script,
                file_name="install.sh",
                mime="text/x-shellscript",
                use_container_width=True
            )
        else:
            st.warning("No hay proyecto para desplegar")
    
    st.markdown("---")
    st.markdown("**Archivos del Proyecto**")
    
    for filename in st.session_state.files.keys():
        col1, col2 = st.columns([4, 1])
        with col1:
            is_current = filename == st.session_state.current_file
            if st.button(
                f"{'📄 ' if not is_current else '📝 '}{filename}",
                key=f"file_{filename}",
                use_container_width=True,
                type="primary" if is_current else "secondary"
            ):
                st.session_state.files[st.session_state.current_file] = st.session_state.code
                st.session_state.current_file = filename
                st.session_state.code = st.session_state.files[filename]
                st.rerun()
        with col2:
            if len(st.session_state.files) > 1:
                if st.button("🗑️", key=f"del_{filename}"):
                    if filename == st.session_state.current_file:
                        remaining = [f for f in st.session_state.files.keys() if f != filename]
                        st.session_state.current_file = remaining[0]
                        st.session_state.code = st.session_state.files[remaining[0]]
                    del st.session_state.files[filename]
                    st.rerun()
    
    st.markdown("---")
    new_file = st.text_input("Nuevo archivo:", placeholder="NombreClase.java", key="new_file_input")
    if st.button("➕ Crear Archivo", use_container_width=True):
        if new_file:
            filename = new_file if new_file.endswith('.java') else f"{new_file}.java"
            if filename not in st.session_state.files:
                class_name = filename.replace('.java', '')
                st.session_state.files[filename] = f'''public class {class_name} {{
    // Tu código aquí
}}'''
                st.session_state.files[st.session_state.current_file] = st.session_state.code
                st.session_state.current_file = filename
                st.session_state.code = st.session_state.files[filename]
                st.rerun()
            else:
                st.warning("El archivo ya existe")
        else:
            st.warning("Escribe un nombre para el archivo")
    
    uploaded_files = st.file_uploader(
        "📂 Importar proyecto",
        type=['java', 'zip'],
        accept_multiple_files=True,
        key="java_uploader",
        help="Sube archivos .java o un .zip con tu proyecto"
    )
    if uploaded_files:
        if st.button("📥 Importar Proyecto", use_container_width=True):
            st.session_state.files[st.session_state.current_file] = st.session_state.code
            imported_count = 0
            skipped_count = 0
            imported_files = {}
            
            for uploaded_file in uploaded_files:
                if uploaded_file.name.endswith('.zip'):
                    try:
                        zip_bytes = io.BytesIO(uploaded_file.read())
                        with zipfile.ZipFile(zip_bytes, 'r') as zip_ref:
                            for file_info in zip_ref.filelist:
                                if file_info.filename.endswith('.java') and not file_info.is_dir():
                                    base_filename = os.path.basename(file_info.filename)
                                    if not base_filename:
                                        continue
                                    final_filename = base_filename
                                    if final_filename in imported_files or final_filename in st.session_state.files:
                                        clean_path = file_info.filename.replace('/', '_').replace('\\', '_')
                                        final_filename = clean_path
                                        if final_filename in imported_files or final_filename in st.session_state.files:
                                            skipped_count += 1
                                            continue
                                    try:
                                        content = zip_ref.read(file_info.filename).decode('utf-8')
                                        imported_files[final_filename] = content
                                        imported_count += 1
                                    except UnicodeDecodeError:
                                        skipped_count += 1
                    except Exception as e:
                        st.error(f"Error al leer ZIP: {e}")
                elif uploaded_file.name.endswith('.java'):
                    filename = uploaded_file.name
                    if filename in imported_files or filename in st.session_state.files:
                        skipped_count += 1
                        continue
                    try:
                        content = uploaded_file.read().decode('utf-8')
                        imported_files[filename] = content
                        imported_count += 1
                    except UnicodeDecodeError:
                        skipped_count += 1
            
            if imported_count > 0:
                st.session_state.files.update(imported_files)
                first_imported = list(imported_files.keys())[0]
                st.session_state.current_file = first_imported
                st.session_state.code = st.session_state.files[first_imported]
                st.session_state.current_project_name = "Proyecto Importado"
                msg = f"✅ {imported_count} archivo(s) importado(s)"
                if skipped_count > 0:
                    msg += f" ({skipped_count} omitido(s))"
                st.success(msg)
                st.rerun()
            else:
                st.warning("No se encontraron archivos .java para importar")
    
    st.markdown("---")
    st.markdown("**Gestión de Proyectos**")
    
    st.caption(f"Proyecto: {st.session_state.current_project_name}")
    
    save_name = st.text_input("Nombre del proyecto:", value=st.session_state.current_project_name if st.session_state.current_project_name != "Proyecto Sin Guardar" else "", placeholder="Mi Proyecto Java", key="save_project_name")
    if st.button("💾 Guardar Proyecto", use_container_width=True):
        if save_name:
            st.session_state.files[st.session_state.current_file] = st.session_state.code
            try:
                save_project(save_name, st.session_state.files, st.session_state.chat_messages)
                st.session_state.current_project_name = save_name
                st.session_state.saved_projects = list_projects()
                st.success(f"Proyecto '{save_name}' guardado")
            except Exception as e:
                st.error(f"Error al guardar: {e}")
        else:
            st.warning("Escribe un nombre para el proyecto")
    
    st.session_state.saved_projects = list_projects()
    if st.session_state.saved_projects:
        st.markdown("**Proyectos Guardados**")
        for proj in st.session_state.saved_projects[:5]:
            col1, col2 = st.columns([4, 1])
            with col1:
                if st.button(f"📁 {proj['name']}", key=f"load_{proj['id']}", use_container_width=True):
                    loaded = load_project(proj['id'])
                    if loaded:
                        st.session_state.files = loaded['files']
                        st.session_state.current_file = list(loaded['files'].keys())[0]
                        st.session_state.code = st.session_state.files[st.session_state.current_file]
                        st.session_state.current_project_name = loaded['name']
                        st.session_state.chat_messages = loaded.get('chat_history', [])
                        st.session_state.console_output = f"✅ Proyecto '{loaded['name']}' cargado"
                        st.rerun()
            with col2:
                if st.button("🗑️", key=f"del_proj_{proj['id']}"):
                    if delete_project(proj['id']):
                        st.session_state.saved_projects = list_projects()
                        st.rerun()
    
    st.markdown("---")
    st.markdown("**Librerías Externas**")
    
    installed_libs = list_installed_libraries()
    if installed_libs:
        st.caption(f"{len(installed_libs)} librería(s) instalada(s)")
        with st.expander("Ver librerías instaladas"):
            for lib in installed_libs:
                col1, col2 = st.columns([4, 1])
                with col1:
                    st.text(lib)
                with col2:
                    if st.button("❌", key=f"rm_{lib}"):
                        result = remove_library(lib)
                        if result['success']:
                            st.rerun()
    else:
        st.caption("Sin librerías instaladas")
    
    lib_options = list(COMMON_LIBRARIES.keys())
    selected_lib = st.selectbox("Agregar librería:", ["-- Seleccionar --"] + lib_options, key="lib_select")
    
    if st.button("📦 Instalar Librería", use_container_width=True):
        if selected_lib and selected_lib != "-- Seleccionar --":
            with st.spinner(f"Descargando {selected_lib}..."):
                result = download_library(selected_lib)
                if result['success']:
                    st.success(result['message'])
                    st.rerun()
                else:
                    st.error(result['error'])
    
    with st.expander("Librería personalizada (Maven)"):
        custom_group = st.text_input("Group ID:", placeholder="com.example", key="custom_group")
        custom_artifact = st.text_input("Artifact ID:", placeholder="mi-libreria", key="custom_artifact")
        custom_version = st.text_input("Versión:", placeholder="1.0.0", key="custom_version")
        if st.button("Instalar personalizada", use_container_width=True):
            if custom_group and custom_artifact and custom_version:
                with st.spinner("Descargando..."):
                    result = download_custom_library(custom_group, custom_artifact, custom_version)
                    if result['success']:
                        st.success(result['message'])
                        st.rerun()
                    else:
                        st.error(result['error'])
            else:
                st.warning("Completa todos los campos")
    
    st.markdown("---")
    st.markdown("**Información**")
    java_version = get_java_version()
    st.code(java_version, language=None)
    st.caption(f"Archivos: {len(st.session_state.files)}")
    st.caption(f"Librerías: {len(installed_libs)}")
    
    st.markdown("---")
    if st.button("Limpiar Chat", use_container_width=True):
        st.session_state.chat_messages = []
        st.rerun()
    
    if st.button("Nuevo Proyecto", use_container_width=True):
        st.session_state.files = DEFAULT_FILES.copy()
        st.session_state.current_file = list(DEFAULT_FILES.keys())[0]
        st.session_state.code = st.session_state.files[st.session_state.current_file]
        st.session_state.current_project_name = "Proyecto Sin Guardar"
        st.rerun()

st.markdown('<p class="main-header">☕ Java IDE con Claude AI</p>', unsafe_allow_html=True)
st.markdown('<p class="sub-header">Desarrolla en Java con asistencia de inteligencia artificial</p>', unsafe_allow_html=True)

if st.session_state.get('show_generate_modal', False):
    with st.container():
        st.markdown("### Generar Código Java")
        description = st.text_area(
            "Describe qué quieres crear:",
            placeholder="Ej: Una clase que calcule el factorial de un número",
            height=100
        )
        col1, col2 = st.columns(2)
        with col1:
            if st.button("Generar", use_container_width=True):
                if description:
                    with st.spinner("Generando código..."):
                        try:
                            generated_code = generate_java_code(description)
                            class_name = find_class_name(generated_code)
                            filename = f"{class_name}.java"
                            st.session_state.files[st.session_state.current_file] = st.session_state.code
                            st.session_state.files[filename] = generated_code
                            st.session_state.current_file = filename
                            st.session_state.code = generated_code
                            st.session_state.show_generate_modal = False
                            st.session_state.ai_notifications.append({
                                "type": "generation",
                                "content": f"He generado `{filename}` basado en: *{description}*"
                            })
                            st.rerun()
                        except Exception as e:
                            st.error(f"Error al generar: {e}")
                else:
                    st.warning("Escribe una descripción")
        with col2:
            if st.button("Cancelar", use_container_width=True):
                st.session_state.show_generate_modal = False
                st.rerun()
        st.markdown("---")

if st.session_state.get('show_architect_modal', False):
    with st.container():
        st.markdown("### 🏗️ Modo Arquitecto")
        st.markdown("*Analiza y planifica tu proyecto antes de implementarlo*")
        
        architect_description = st.text_area(
            "¿Qué quieres construir?",
            placeholder="Ej: Un sistema de gestión de inventario con productos, categorías y reportes",
            height=100,
            key="architect_input"
        )
        
        col1, col2, col3 = st.columns(3)
        with col1:
            if st.button("📋 Analizar", use_container_width=True):
                if architect_description:
                    with st.spinner("Analizando requisitos..."):
                        try:
                            plan = architect_analyze(architect_description, st.session_state.files)
                            st.session_state.architect_plan = plan
                        except Exception as e:
                            st.error(f"Error: {e}")
                else:
                    st.warning("Describe qué quieres construir")
        with col2:
            if st.button("🚀 Implementar Plan", use_container_width=True, disabled=not st.session_state.architect_plan):
                if st.session_state.architect_plan:
                    plan_summary = st.session_state.architect_plan.get('analysis', '')
                    files_needed = st.session_state.architect_plan.get('files_needed', [])
                    files_list = ", ".join([f.get('name', '') for f in files_needed])
                    
                    implementation_prompt = f"Implementa el siguiente plan:\n{plan_summary}\n\nArchivos necesarios: {files_list}\n\nCrea todos los archivos con código completo y funcional."
                    
                    st.session_state.chat_messages.append({
                        "role": "user",
                        "content": implementation_prompt
                    })
                    st.session_state.show_architect_modal = False
                    st.session_state.architect_plan = None
                    st.rerun()
        with col3:
            if st.button("Cerrar", use_container_width=True):
                st.session_state.show_architect_modal = False
                st.session_state.architect_plan = None
                st.rerun()
        
        if st.session_state.architect_plan:
            plan = st.session_state.architect_plan
            st.markdown("---")
            st.markdown("#### 📊 Análisis")
            st.info(plan.get('analysis', 'Sin análisis'))
            
            if plan.get('files_needed'):
                st.markdown("#### 📁 Archivos Necesarios")
                for f in plan['files_needed']:
                    deps = ", ".join(f.get('dependencies', [])) if f.get('dependencies') else "Ninguna"
                    st.markdown(f"- **{f.get('name', 'N/A')}**: {f.get('purpose', '')} *(deps: {deps})*")
            
            if plan.get('implementation_plan'):
                st.markdown("#### 📝 Plan de Implementación")
                for step in plan['implementation_plan']:
                    files = ", ".join(step.get('files', []))
                    st.markdown(f"{step.get('step', '')}. {step.get('description', '')} *({files})*")
            
            complexity = plan.get('estimated_complexity', 'desconocida')
            complexity_emoji = {"baja": "🟢", "media": "🟡", "alta": "🔴"}.get(complexity, "⚪")
            st.markdown(f"#### Complejidad: {complexity_emoji} {complexity.capitalize()}")
            
            if plan.get('recommendations'):
                st.markdown("#### 💡 Recomendaciones")
                for rec in plan['recommendations']:
                    st.markdown(f"- {rec}")
        
        st.markdown("---")

if st.session_state.get('show_templates_modal', False):
    with st.container():
        st.markdown("### 📋 Plantillas de Código")
        st.markdown("*Patrones de diseño listos para usar en tu proyecto*")
        
        templates = get_available_templates()
        
        template_cols = st.columns(3)
        for idx, template in enumerate(templates):
            with template_cols[idx % 3]:
                if st.button(
                    f"**{template['name']}**\n{template['desc']}", 
                    key=f"template_{template['id']}",
                    use_container_width=True
                ):
                    template_data = get_code_template(template['id'])
                    if template_data:
                        files_added = []
                        for file_info in template_data['files']:
                            filename = file_info['name']
                            if filename not in st.session_state.files:
                                st.session_state.files[filename] = file_info['code']
                                files_added.append(filename)
                            else:
                                base = filename.replace('.java', '')
                                counter = 1
                                new_name = f"{base}{counter}.java"
                                while new_name in st.session_state.files:
                                    counter += 1
                                    new_name = f"{base}{counter}.java"
                                st.session_state.files[new_name] = file_info['code']
                                files_added.append(new_name)
                        
                        if files_added:
                            st.session_state.current_file = files_added[0]
                            st.session_state.code = st.session_state.files[files_added[0]]
                            st.session_state.ai_notifications.append({
                                "type": "template",
                                "content": f"**Plantilla {template_data['name']} añadida:**\n\n{template_data['description']}\n\nArchivos creados: {', '.join([f'`{f}`' for f in files_added])}"
                            })
                        
                        st.session_state.show_templates_modal = False
                        st.rerun()
        
        st.markdown("---")
        if st.button("Cerrar", use_container_width=True, key="close_templates"):
            st.session_state.show_templates_modal = False
            st.rerun()
        st.markdown("---")

col_editor, col_chat = st.columns([3, 2])

with col_editor:
    st.markdown(f"### Editor: {st.session_state.current_file}")
    
    code = st_ace(
        value=st.session_state.code,
        language='java',
        theme='monokai',
        height=400,
        font_size=14,
        tab_size=4,
        show_gutter=True,
        show_print_margin=False,
        wrap=True,
        auto_update=True,
        key=f'code_editor_{st.session_state.current_file}'
    )
    
    if code != st.session_state.code:
        st.session_state.code = code
        st.session_state.files[st.session_state.current_file] = code
    
    btn_col1, btn_col2, btn_col3, btn_col4 = st.columns(4)
    
    with btn_col1:
        if st.button("Compilar Todo", type="primary", use_container_width=True):
            st.session_state.files[st.session_state.current_file] = st.session_state.code
            
            with st.spinner("Detectando dependencias..."):
                dep_result = install_detected_dependencies(st.session_state.files)
                dep_msg = ""
                if dep_result.get('installed'):
                    dep_msg = f"📦 {dep_result['message']}\n\n"
            
            with st.spinner("Compilando..."):
                if len(st.session_state.files) == 1:
                    result = compile_java(list(st.session_state.files.values())[0])
                else:
                    result = compile_multi_file(st.session_state.files)
                if result['success']:
                    st.session_state.console_output = f"{dep_msg}✅ {result['message']}"
                    st.session_state.last_error = None
                else:
                    error_msg = result['error']
                    st.session_state.console_output = f"{dep_msg}❌ Error de compilación:\n{error_msg}"
                    st.session_state.last_error = error_msg
                    
                    if st.session_state.auto_fix_enabled:
                        with st.spinner("🔧 Claude está analizando y corrigiendo el error..."):
                            try:
                                explanation, fix_actions, needs_more = auto_fix_error(
                                    error_msg, 
                                    st.session_state.files
                                )
                                
                                fixed_files = []
                                for action in fix_actions:
                                    action_type = action.get("type", "")
                                    filename = action.get("file", "")
                                    
                                    if not filename.endswith(".java") or "/" in filename or "\\" in filename:
                                        continue
                                    
                                    if action_type in ["modify", "create"]:
                                        content = action.get("content", "")
                                        if content and len(content) < 50000:
                                            st.session_state.files[filename] = content
                                            if st.session_state.current_file == filename:
                                                st.session_state.code = content
                                            action_label = "corregido" if action_type == "modify" else "creado"
                                            fixed_files.append(f"{filename} ({action_label})")
                                    
                                    elif action_type == "delete":
                                        if filename in st.session_state.files:
                                            del st.session_state.files[filename]
                                            if st.session_state.current_file == filename:
                                                remaining = list(st.session_state.files.keys())
                                                if remaining:
                                                    st.session_state.current_file = remaining[0]
                                                    st.session_state.code = st.session_state.files[remaining[0]]
                                            fixed_files.append(f"{filename} (eliminado)")
                                    
                                    elif action_type == "rename":
                                        new_name = action.get("newName", "")
                                        if new_name and filename in st.session_state.files:
                                            content = st.session_state.files[filename]
                                            del st.session_state.files[filename]
                                            st.session_state.files[new_name] = content
                                            if st.session_state.current_file == filename:
                                                st.session_state.current_file = new_name
                                            fixed_files.append(f"{filename} → {new_name}")
                                
                                if fixed_files:
                                    st.session_state.console_output += f"\n\n🔧 **Claude corrigió automáticamente:** {', '.join(fixed_files)}\n\n{explanation}\n\n*Vuelve a compilar para verificar la corrección.*"
                                    st.session_state.ai_notifications.append({
                                        "type": "fix",
                                        "content": f"**Corrección automática aplicada:**\n\n{explanation}"
                                    })
                                else:
                                    st.session_state.ai_notifications.append({
                                        "type": "error",
                                        "content": f"**Análisis del error:**\n\n{explanation}"
                                    })
                            except Exception as e:
                                st.session_state.ai_notifications.append({
                                    "type": "error",
                                    "content": f"No se pudo analizar el error automáticamente: {e}"
                                })
    
    with btn_col2:
        if st.button("Ejecutar", use_container_width=True):
            st.session_state.files[st.session_state.current_file] = st.session_state.code
            
            dep_result = install_detected_dependencies(st.session_state.files)
            dep_msg = ""
            if dep_result.get('installed'):
                dep_msg = f"📦 Librerías instaladas automáticamente: {', '.join(dep_result['installed'])}\n\n"
            
            with st.spinner("Ejecutando..."):
                if len(st.session_state.files) == 1:
                    result = run_java(list(st.session_state.files.values())[0])
                    class_info = result.get('class_name', 'Main')
                else:
                    result = run_multi_file(st.session_state.files)
                    class_info = result.get('main_class', 'Main')
                if result['success']:
                    st.session_state.console_output = f"{dep_msg}▶️ Ejecución de {class_info}:\n\n{result['output']}"
                elif 'error' in result:
                    st.session_state.console_output = f"{dep_msg}❌ Error:\n{result['error']}"
                else:
                    st.session_state.console_output = f"{dep_msg}⚠️ Código de salida: {result['return_code']}\n{result.get('output', '')}"
    
    with btn_col3:
        if st.button("Crear JAR", use_container_width=True):
            st.session_state.files[st.session_state.current_file] = st.session_state.code
            
            dep_result = install_detected_dependencies(st.session_state.files)
            dep_msg = ""
            if dep_result.get('installed'):
                dep_msg = f"📦 Librerías instaladas: {', '.join(dep_result['installed'])}\n"
            
            with st.spinner("Creando JAR..."):
                if len(st.session_state.files) == 1:
                    result = create_jar(list(st.session_state.files.values())[0])
                else:
                    result = create_multi_jar(st.session_state.files)
                if result['success']:
                    st.session_state.console_output = f"{dep_msg}📦 {result['message']}"
                    if result['jar_path'] not in st.session_state.jar_files:
                        st.session_state.jar_files.append(result['jar_path'])
                else:
                    st.session_state.console_output = f"{dep_msg}❌ {result['error']}"
    
    with btn_col4:
        if st.button("Limpiar Consola", use_container_width=True):
            st.session_state.console_output = ""
            st.rerun()
    
    st.markdown("### Consola de Salida")
    if st.session_state.console_output:
        if st.session_state.console_output.startswith("✅") or st.session_state.console_output.startswith("▶️") or st.session_state.console_output.startswith("📦"):
            st.success(st.session_state.console_output)
        else:
            st.error(st.session_state.console_output)
    else:
        st.info("La salida de compilación y ejecución aparecerá aquí...")
    
    if st.session_state.jar_files:
        st.markdown("### Archivos JAR Generados")
        for jar_path in st.session_state.jar_files:
            if os.path.exists(jar_path):
                jar_name = os.path.basename(jar_path)
                with open(jar_path, 'rb') as f:
                    st.download_button(
                        label=f"📥 Descargar {jar_name}",
                        data=f.read(),
                        file_name=jar_name,
                        mime="application/java-archive",
                        use_container_width=True
                    )

with col_chat:
    chat_header_col1, chat_header_col2 = st.columns([3, 1])
    with chat_header_col1:
        st.markdown("### Chat con Claude AI")
    with chat_header_col2:
        if st.button("🗑️ Limpiar", key="clear_chat"):
            st.session_state.chat_messages = []
            if st.session_state.current_project_name != "Proyecto Sin Guardar":
                update_chat_history(st.session_state.current_project_name, [])
            st.rerun()
    
    if st.session_state.ai_notifications:
        with st.expander("Respuestas de Acciones Rápidas", expanded=True):
            for notif in st.session_state.ai_notifications[-3:]:
                st.markdown(notif["content"])
                st.markdown("---")
            if st.button("Limpiar Notificaciones", key="clear_notifs"):
                st.session_state.ai_notifications = []
                st.rerun()
    
    has_extras = st.session_state.ai_notifications
    chat_container = st.container(height=300 if has_extras else 400)
    
    with chat_container:
        if not st.session_state.chat_messages:
            st.markdown("""
            *Hola! Soy tu asistente de desarrollo Java.*
            
            Puedo ayudarte a:
            - **Crear archivos**: "Crea una clase Usuario con nombre y email"
            - **Modificar código**: "Agrega un método para validar email"
            - **Explicar errores** y responder preguntas sobre Java
            
            *Los cambios se aplican automáticamente al proyecto!*
            """)
        else:
            for msg in st.session_state.chat_messages:
                with st.chat_message(msg["role"]):
                    st.markdown(msg["content"])
    
    user_input = st.chat_input("Escribe tu pregunta o pide crear/modificar código...")
    
    if user_input:
        st.session_state.chat_messages.append({
            "role": "user",
            "content": user_input
        })
        
        with st.spinner("Claude está pensando..."):
            try:
                all_applied_actions = []
                max_continuations = 10
                continuation_count = 0
                
                response, actions, needs_continuation = chat_with_actions(
                    st.session_state.chat_messages,
                    get_all_code(),
                    st.session_state.files
                )
                
                while True:
                    actions_in_this_round = 0
                    for action in actions:
                        filename = action.get("file", "")
                        action_type = action.get("type", "create")
                        
                        if not filename.endswith(".java") or "/" in filename or "\\" in filename:
                            continue
                        
                        st.session_state.files[st.session_state.current_file] = st.session_state.code
                        
                        if action_type in ["create", "modify"]:
                            content = action.get("content", "")
                            if len(content) > 50000:
                                continue
                            st.session_state.files[filename] = content
                            st.session_state.current_file = filename
                            st.session_state.code = content
                            action_label = "creado" if action_type == "create" else "modificado"
                            all_applied_actions.append(f"**{filename}** {action_label}")
                            actions_in_this_round += 1
                        
                        elif action_type == "delete":
                            if filename in st.session_state.files:
                                del st.session_state.files[filename]
                                if st.session_state.current_file == filename:
                                    remaining_files = list(st.session_state.files.keys())
                                    if remaining_files:
                                        st.session_state.current_file = remaining_files[0]
                                        st.session_state.code = st.session_state.files[remaining_files[0]]
                                all_applied_actions.append(f"**{filename}** eliminado")
                                actions_in_this_round += 1
                        
                        elif action_type == "rename":
                            new_name = action.get("newName", "")
                            if new_name and filename in st.session_state.files:
                                content = st.session_state.files[filename]
                                del st.session_state.files[filename]
                                st.session_state.files[new_name] = content
                                if st.session_state.current_file == filename:
                                    st.session_state.current_file = new_name
                                all_applied_actions.append(f"**{filename}** → **{new_name}**")
                                actions_in_this_round += 1
                    
                    should_continue = (
                        needs_continuation and 
                        continuation_count < max_continuations and 
                        actions_in_this_round > 0
                    )
                    
                    if should_continue:
                        continuation_count += 1
                        st.toast(f"Continuando automáticamente... ({continuation_count})")
                        
                        st.session_state.chat_messages.append({
                            "role": "assistant",
                            "content": response
                        })
                        st.session_state.chat_messages.append({
                            "role": "user",
                            "content": "Continúa con el siguiente archivo."
                        })
                        
                        response, actions, needs_continuation = chat_with_actions(
                            st.session_state.chat_messages,
                            get_all_code(),
                            st.session_state.files
                        )
                    else:
                        break
                
                final_response = response
                
                if all_applied_actions:
                    dep_result = install_detected_dependencies(st.session_state.files)
                    
                    actions_msg = "\n\n---\n📁 " + " | ".join(all_applied_actions)
                    if continuation_count > 0:
                        actions_msg += f"\n\n*({continuation_count + 1} archivos procesados automáticamente)*"
                    
                    if dep_result.get('installed'):
                        actions_msg += f"\n\n📦 **Librerías instaladas automáticamente:** {', '.join(dep_result['installed'])}"
                    
                    final_response += actions_msg
                
                st.session_state.chat_messages.append({
                    "role": "assistant",
                    "content": final_response
                })
            except Exception as e:
                st.session_state.chat_messages.append({
                    "role": "assistant",
                    "content": f"Error al conectar con Claude: {e}"
                })
        
        if st.session_state.current_project_name != "Proyecto Sin Guardar":
            update_chat_history(st.session_state.current_project_name, st.session_state.chat_messages)
        
        st.rerun()

st.markdown("---")
st.markdown(
    "<p style='text-align: center; color: #888;'>Java IDE con Claude AI | Compilación y ejecución en la nube</p>",
    unsafe_allow_html=True
)
