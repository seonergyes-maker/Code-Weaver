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
    chat_with_actions
)
from database import save_project, load_project, list_projects, delete_project
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
                save_project(save_name, st.session_state.files)
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
                else:
                    st.session_state.console_output = f"{dep_msg}❌ Error de compilación:\n{result['error']}"
    
    with btn_col2:
        if st.button("Ejecutar", use_container_width=True):
            st.session_state.files[st.session_state.current_file] = st.session_state.code
            with st.spinner("Ejecutando..."):
                if len(st.session_state.files) == 1:
                    result = run_java(list(st.session_state.files.values())[0])
                    class_info = result.get('class_name', 'Main')
                else:
                    result = run_multi_file(st.session_state.files)
                    class_info = result.get('main_class', 'Main')
                if result['success']:
                    st.session_state.console_output = f"▶️ Ejecución de {class_info}:\n\n{result['output']}"
                elif 'error' in result:
                    st.session_state.console_output = f"❌ Error:\n{result['error']}"
                else:
                    st.session_state.console_output = f"⚠️ Código de salida: {result['return_code']}\n{result.get('output', '')}"
    
    with btn_col3:
        if st.button("Crear JAR", use_container_width=True):
            st.session_state.files[st.session_state.current_file] = st.session_state.code
            with st.spinner("Creando JAR..."):
                if len(st.session_state.files) == 1:
                    result = create_jar(list(st.session_state.files.values())[0])
                else:
                    result = create_multi_jar(st.session_state.files)
                if result['success']:
                    st.session_state.console_output = f"📦 {result['message']}"
                    if result['jar_path'] not in st.session_state.jar_files:
                        st.session_state.jar_files.append(result['jar_path'])
                else:
                    st.session_state.console_output = f"❌ {result['error']}"
    
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
    st.markdown("### Chat con Claude AI")
    
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
                response, actions = chat_with_actions(
                    st.session_state.chat_messages,
                    get_all_code(),
                    st.session_state.files
                )
                
                applied_actions = []
                for action in actions:
                    filename = action.get("file", "")
                    content = action.get("content", "")
                    action_type = action.get("type", "create")
                    
                    if not filename.endswith(".java") or "/" in filename or "\\" in filename:
                        continue
                    if len(content) > 50000:
                        continue
                    
                    st.session_state.files[st.session_state.current_file] = st.session_state.code
                    st.session_state.files[filename] = content
                    st.session_state.current_file = filename
                    st.session_state.code = content
                    
                    action_label = "creado" if action_type == "create" else "modificado"
                    applied_actions.append(f"**{filename}** {action_label}")
                
                if applied_actions:
                    actions_msg = "\n\n---\n📁 " + " | ".join(applied_actions)
                    response += actions_msg
                
                st.session_state.chat_messages.append({
                    "role": "assistant",
                    "content": response
                })
            except Exception as e:
                st.session_state.chat_messages.append({
                    "role": "assistant",
                    "content": f"Error al conectar con Claude: {e}"
                })
        
        st.rerun()

st.markdown("---")
st.markdown(
    "<p style='text-align: center; color: #888;'>Java IDE con Claude AI | Compilación y ejecución en la nube</p>",
    unsafe_allow_html=True
)
