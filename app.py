import streamlit as st
from streamlit_ace import st_ace
import os
import zipfile
import io

from java_compiler import (
    compile_java, create_jar, run_java, get_java_version,
    compile_multi_file, run_multi_file, create_multi_jar, find_class_name,
    find_main_class
)
from database import save_project, load_project, list_projects, delete_project
from dependency_manager import (
    download_library, download_custom_library, list_installed_libraries,
    remove_library, get_available_libraries, COMMON_LIBRARIES,
    install_detected_dependencies
)

st.set_page_config(
    page_title="Java IDE",
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
    /* Editor más grande y responsivo */
    [data-testid="stVerticalBlock"] > div:has(> iframe) {
        min-height: 60vh !important;
    }
    .ace_editor {
        min-height: 500px !important;
    }
    /* Reducir padding para más espacio */
    .block-container {
        padding-top: 1rem !important;
        padding-bottom: 1rem !important;
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
if 'jar_files' not in st.session_state:
    st.session_state.jar_files = []
if 'new_file_name' not in st.session_state:
    st.session_state.new_file_name = ""
if 'current_project_name' not in st.session_state:
    st.session_state.current_project_name = "Proyecto Sin Guardar"
if 'saved_projects' not in st.session_state:
    st.session_state.saved_projects = list_projects()
if 'run_on_save' not in st.session_state:
    st.session_state.run_on_save = True
if 'previous_code' not in st.session_state:
    st.session_state.previous_code = st.session_state.code

with st.sidebar:
    st.markdown("### ☕ Java IDE")
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
    
    # Buscador de código
    st.markdown("**🔍 Buscar en Proyecto**")
    search_query = st.text_input("Buscar:", placeholder="texto a buscar...", key="search_code_input")
    
    if search_query and len(search_query) >= 2:
        search_results = []
        query_lower = search_query.lower()
        
        for filename, content in st.session_state.files.items():
            lines = content.split('\n')
            for line_num, line in enumerate(lines, 1):
                if query_lower in line.lower():
                    search_results.append({
                        'file': filename,
                        'line': line_num,
                        'content': line.strip()[:60]
                    })
        
        if search_results:
            st.caption(f"📋 {len(search_results)} resultado(s)")
            with st.container(height=200):
                for i, result in enumerate(search_results[:20]):
                    result_text = f"**{result['file']}** :{result['line']}"
                    if st.button(result_text, key=f"search_result_{i}", use_container_width=True, help=result['content']):
                        st.session_state.files[st.session_state.current_file] = st.session_state.code
                        st.session_state.current_file = result['file']
                        st.session_state.code = st.session_state.files[result['file']]
                        st.session_state.search_goto_line = result['line']
                        st.rerun()
                    st.caption(f"└─ `{result['content']}`")
            if len(search_results) > 20:
                st.caption(f"... y {len(search_results) - 20} más")
        else:
            st.caption("Sin resultados")
    
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
                save_project(save_name, st.session_state.files, [])
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
    st.markdown("**Herramientas**")
    
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

Generado por Java IDE
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
# 
# USO: chmod +x install.sh && sudo ./install.sh
# OPCIONES:
#   --no-service    No crear servicio systemd
#   --no-start      No iniciar automáticamente
#   --uninstall     Desinstalar la aplicación
# =============================================================================

set -e

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
echo -e "${{BLUE}}╚══════════════════════════════════════════════════════════════╝${{NC}}"

SCRIPT_DIR="$(cd "$(dirname "${{BASH_SOURCE[0]}}")" && pwd)"

echo -e "\\n${{YELLOW}}[1/7] Verificando permisos...${{NC}}"
if [ "$EUID" -ne 0 ]; then
    echo -e "${{RED}}Este script necesita ejecutarse como root${{NC}}"
    echo "Uso: sudo ./install.sh"
    exit 1
fi
echo -e "${{GREEN}}✓ Ejecutando como root${{NC}}"

echo -e "\\n${{YELLOW}}[2/7] Actualizando sistema...${{NC}}"
apt-get update -qq
apt-get install -y -qq curl wget unzip > /dev/null
echo -e "${{GREEN}}✓ Sistema actualizado${{NC}}"

echo -e "\\n${{YELLOW}}[3/7] Configurando Java...${{NC}}"
if ! command -v java &> /dev/null; then
    echo "Instalando OpenJDK 17..."
    apt-get install -y openjdk-17-jre-headless > /dev/null
    echo -e "${{GREEN}}✓ Java 17 instalado${{NC}}"
else
    java_version=$(java -version 2>&1 | head -n 1)
    echo -e "${{GREEN}}✓ Java detectado: $java_version${{NC}}"
fi

if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
    echo "export JAVA_HOME=$JAVA_HOME" >> /etc/environment
fi

echo -e "\\n${{YELLOW}}[4/7] Creando estructura de directorios...${{NC}}"
mkdir -p "$APP_DIR"
mkdir -p "$APP_DIR/logs"
mkdir -p "$APP_DIR/config"
mkdir -p "$APP_DIR/libs"
echo -e "${{GREEN}}✓ Directorios creados en $APP_DIR${{NC}}"

echo -e "\\n${{YELLOW}}[5/7] Copiando archivos...${{NC}}"

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

if [ -d "$SCRIPT_DIR/libs" ]; then
    cp -r "$SCRIPT_DIR/libs/"*.jar "$APP_DIR/libs/" 2>/dev/null || true
    LIB_COUNT=$(ls -1 "$APP_DIR/libs/"*.jar 2>/dev/null | wc -l)
    echo -e "${{GREEN}}✓ $LIB_COUNT librerías copiadas${{NC}}"
fi

if [ -d "$SCRIPT_DIR/config" ]; then
    cp -r "$SCRIPT_DIR/config/"* "$APP_DIR/config/" 2>/dev/null || true
fi

echo -e "\\n${{YELLOW}}[6/7] Configurando scripts de ejecución...${{NC}}"

cat > "$APP_DIR/run.sh" << 'RUNSCRIPT'
#!/bin/bash
APP_DIR="/opt/{project_name}"
JAR="$APP_DIR/{jar_name}"
LOG_DIR="$APP_DIR/logs"
JAVA_OPTS="-Xmx512m -Xms128m"

cd "$APP_DIR"

CLASSPATH="$JAR"
if [ -d "$APP_DIR/libs" ]; then
    for lib in "$APP_DIR/libs/"*.jar; do
        [ -f "$lib" ] && CLASSPATH="$CLASSPATH:$lib"
    done
fi

exec java $JAVA_OPTS -cp "$CLASSPATH" -jar "$JAR" "$@"
RUNSCRIPT
chmod +x "$APP_DIR/run.sh"

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

ln -sf "$APP_DIR/run.sh" "/usr/local/bin/$APP_NAME"
echo -e "${{GREEN}}✓ Comando '$APP_NAME' disponible globalmente${{NC}}"

if [ "$CREATE_SERVICE" = true ]; then
    echo -e "\\n${{YELLOW}}[7/7] Configurando servicio systemd...${{NC}}"
    
    cat > /etc/systemd/system/$SERVICE_NAME.service << SERVICEEOF
[Unit]
Description={project_name} Java Application
After=network.target
Wants=network-online.target

[Service]
Type=simple
User=root
Group=root
WorkingDirectory=$APP_DIR
ExecStart=/usr/bin/java -Xmx512m -jar $APP_DIR/$JAR_NAME --headless --autostart
ExecStop=/bin/kill -TERM \\$MAINPID
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=$APP_NAME

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
    st.markdown("**⚙️ Configuración**")
    
    st.session_state.run_on_save = st.checkbox(
        "Ejecutar al guardar",
        value=st.session_state.run_on_save,
        help="Ejecuta automáticamente la aplicación cuando se actualiza el código"
    )
    
    st.markdown("---")
    st.markdown("**Información**")
    java_version = get_java_version()
    st.code(java_version, language=None)
    st.caption(f"Archivos: {len(st.session_state.files)}")
    st.caption(f"Librerías: {len(installed_libs)}")
    
    st.markdown("---")
    if st.button("🆕 Nuevo Proyecto", use_container_width=True):
        st.session_state.files = DEFAULT_FILES.copy()
        st.session_state.current_file = list(DEFAULT_FILES.keys())[0]
        st.session_state.code = st.session_state.files[st.session_state.current_file]
        st.session_state.current_project_name = "Proyecto Sin Guardar"
        st.rerun()

st.markdown('<p class="main-header">☕ Java IDE</p>', unsafe_allow_html=True)
st.markdown('<p class="sub-header">Desarrolla aplicaciones Java en la nube</p>', unsafe_allow_html=True)

st.markdown(f"### Editor: {st.session_state.current_file}")

code = st_ace(
    value=st.session_state.code,
    language='java',
    theme='monokai',
    height=600,
    font_size=14,
    tab_size=4,
    show_gutter=True,
    show_print_margin=False,
    wrap=True,
    auto_update=True,
    key=f'code_editor_{st.session_state.current_file}'
)

code_changed = code != st.session_state.code
if code_changed:
    st.session_state.code = code
    st.session_state.files[st.session_state.current_file] = code
    
    if st.session_state.run_on_save:
        dep_result = install_detected_dependencies(st.session_state.files)
        dep_msg = ""
        if dep_result.get('installed'):
            dep_msg = f"📦 {dep_result['message']}\n\n"
        
        if len(st.session_state.files) == 1:
            result = run_java(list(st.session_state.files.values())[0])
            class_info = result.get('class_name', 'Main')
        else:
            result = run_multi_file(st.session_state.files)
            class_info = result.get('main_class', 'Main')
        
        if result['success']:
            st.session_state.console_output = f"{dep_msg}🔄 Auto-ejecución de {class_info}:\n\n{result['output']}"
        elif 'error' in result:
            st.session_state.console_output = f"{dep_msg}❌ Error:\n{result['error']}"
        else:
            st.session_state.console_output = f"{dep_msg}⚠️ Código de salida: {result['return_code']}\n{result.get('output', '')}"

btn_col1, btn_col2, btn_col3, btn_col4 = st.columns(4)

with btn_col1:
    if st.button("▶️ Compilar Todo", type="primary", use_container_width=True):
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
            else:
                error_msg = result['error']
                st.session_state.console_output = f"{dep_msg}❌ Error de compilación:\n{error_msg}"

with btn_col2:
    if st.button("🚀 Ejecutar", use_container_width=True):
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
    if st.button("📦 Crear JAR", use_container_width=True):
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
    if st.button("🧹 Limpiar Consola", use_container_width=True):
        st.session_state.console_output = ""
        st.rerun()

# Botón especial para probar conexión LLRP4J (solo si está TestLLRP4J.java)
if 'TestLLRP4J.java' in st.session_state.files or 'LLRP4JReader.java' in st.session_state.files:
    st.markdown("---")
    st.markdown("### 📡 Prueba de Conexión RFID")
    
    llrp_col1, llrp_col2 = st.columns([3, 1])
    
    with llrp_col1:
        reader_ip = st.text_input("IP del Lector FX7500:", value="192.168.1.117", key="reader_ip_input")
    
    with llrp_col2:
        test_duration = st.number_input("Duración (seg):", min_value=5, max_value=120, value=30, key="test_duration")
    
    if st.button("📡 Probar Conexión LLRP4J", type="primary", use_container_width=True):
        st.session_state.files[st.session_state.current_file] = st.session_state.code
        
        # Actualizar la IP en TestLLRP4J.java si existe
        if 'TestLLRP4J.java' in st.session_state.files:
            test_code = st.session_state.files['TestLLRP4J.java']
            # Reemplazar la IP en el código
            import re
            test_code = re.sub(
                r'String readerIP = args\.length > 0 \? args\[0\] : "[^"]+";',
                f'String readerIP = args.length > 0 ? args[0] : "{reader_ip}";',
                test_code
            )
            st.session_state.files['TestLLRP4J.java'] = test_code
        
        with st.spinner(f"Conectando a {reader_ip}..."):
            # Compilar y ejecutar TestLLRP4J con argumentos
            dep_result = install_detected_dependencies(st.session_state.files)
            dep_msg = ""
            if dep_result.get('installed'):
                dep_msg = f"📦 Librerías instaladas: {', '.join(dep_result['installed'])}\n\n"
            
            # Ejecutar con argumentos IP y duración
            from java_compiler import run_multi_file_with_args
            result = run_multi_file_with_args(
                st.session_state.files, 
                main_class="TestLLRP4J",
                args=[reader_ip, str(test_duration)],
                timeout=test_duration + 30
            )
            
            if result['success']:
                st.session_state.console_output = f"{dep_msg}📡 Prueba LLRP4J con {reader_ip}:\n\n{result['output']}"
            elif 'error' in result:
                st.session_state.console_output = f"{dep_msg}❌ Error de conexión:\n{result['error']}"
            else:
                st.session_state.console_output = f"{dep_msg}⚠️ Resultado:\n{result.get('output', '')}"

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

st.markdown("---")
st.markdown(
    "<p style='text-align: center; color: #888;'>Java IDE | Compilación y ejecución en la nube</p>",
    unsafe_allow_html=True
)
