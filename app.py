import streamlit as st
from streamlit_ace import st_ace
import os

from java_compiler import compile_java, create_jar, run_java, get_java_version
from claude_assistant import (
    chat_with_claude, 
    generate_java_code, 
    explain_error, 
    suggest_improvements
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
    .success-box {
        padding: 1rem;
        border-radius: 0.5rem;
        background-color: #1B5E20;
        color: white;
        margin: 0.5rem 0;
    }
    .error-box {
        padding: 1rem;
        border-radius: 0.5rem;
        background-color: #B71C1C;
        color: white;
        margin: 0.5rem 0;
    }
    .console-output {
        font-family: 'Courier New', monospace;
        background-color: #1E1E1E;
        color: #00FF00;
        padding: 1rem;
        border-radius: 0.5rem;
        white-space: pre-wrap;
        max-height: 300px;
        overflow-y: auto;
    }
</style>
""", unsafe_allow_html=True)

DEFAULT_CODE = '''public class Main {
    public static void main(String[] args) {
        System.out.println("Hola desde Java IDE!");
        System.out.println("Escribe tu código aquí...");
    }
}'''

if 'code' not in st.session_state:
    st.session_state.code = DEFAULT_CODE
if 'console_output' not in st.session_state:
    st.session_state.console_output = ""
if 'chat_messages' not in st.session_state:
    st.session_state.chat_messages = []
if 'jar_files' not in st.session_state:
    st.session_state.jar_files = []
if 'ai_notifications' not in st.session_state:
    st.session_state.ai_notifications = []

with st.sidebar:
    st.markdown("### Asistente Claude AI")
    st.markdown("---")
    
    st.markdown("**Acciones Rápidas**")
    
    if st.button("Generar Código", use_container_width=True):
        st.session_state.show_generate_modal = True
    
    if st.button("Sugerir Mejoras", use_container_width=True):
        if st.session_state.code.strip():
            with st.spinner("Analizando código..."):
                try:
                    suggestions = suggest_improvements(st.session_state.code)
                    st.session_state.ai_notifications.append({
                        "type": "suggestions",
                        "content": f"**Sugerencias de mejora:**\n\n{suggestions}"
                    })
                except Exception as e:
                    st.error(f"Error: {e}")
        else:
            st.warning("Escribe código primero")
    
    if st.button("Explicar Último Error", use_container_width=True):
        if "error" in st.session_state.console_output.lower() or "exception" in st.session_state.console_output.lower():
            with st.spinner("Analizando error..."):
                try:
                    explanation = explain_error(st.session_state.console_output, st.session_state.code)
                    st.session_state.ai_notifications.append({
                        "type": "error_explanation",
                        "content": f"**Explicación del error:**\n\n{explanation}"
                    })
                except Exception as e:
                    st.error(f"Error: {e}")
        else:
            st.info("No hay errores recientes para explicar")
    
    st.markdown("---")
    st.markdown("**Información**")
    java_version = get_java_version()
    st.code(java_version, language=None)
    
    st.markdown("---")
    if st.button("Limpiar Chat", use_container_width=True):
        st.session_state.chat_messages = []
        st.rerun()
    
    if st.button("Resetear Código", use_container_width=True):
        st.session_state.code = DEFAULT_CODE
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
                            st.session_state.code = generated_code
                            st.session_state.show_generate_modal = False
                            st.session_state.ai_notifications.append({
                                "type": "generation",
                                "content": f"He generado código basado en: *{description}*"
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
    st.markdown("### Editor de Código Java")
    
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
        key='code_editor'
    )
    
    if code != st.session_state.code:
        st.session_state.code = code
    
    btn_col1, btn_col2, btn_col3, btn_col4 = st.columns(4)
    
    with btn_col1:
        if st.button("Compilar", type="primary", use_container_width=True):
            with st.spinner("Compilando..."):
                result = compile_java(st.session_state.code)
                if result['success']:
                    st.session_state.console_output = f"✅ {result['message']}"
                else:
                    st.session_state.console_output = f"❌ Error de compilación:\n{result['error']}"
    
    with btn_col2:
        if st.button("Ejecutar", use_container_width=True):
            with st.spinner("Ejecutando..."):
                result = run_java(st.session_state.code)
                if result['success']:
                    st.session_state.console_output = f"▶️ Ejecución de {result['class_name']}:\n\n{result['output']}"
                elif 'error' in result:
                    st.session_state.console_output = f"❌ Error:\n{result['error']}"
                else:
                    st.session_state.console_output = f"⚠️ Código de salida: {result['return_code']}\n{result.get('output', '')}"
    
    with btn_col3:
        if st.button("Crear JAR", use_container_width=True):
            with st.spinner("Creando JAR..."):
                result = create_jar(st.session_state.code)
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
    
    chat_container = st.container(height=350 if st.session_state.ai_notifications else 400)
    
    with chat_container:
        if not st.session_state.chat_messages:
            st.markdown("""
            *Hola! Soy tu asistente de desarrollo Java.*
            
            Puedo ayudarte a:
            - Escribir y mejorar código
            - Explicar errores
            - Responder preguntas sobre Java
            
            *Escribe tu pregunta abajo...*
            """)
        else:
            for msg in st.session_state.chat_messages:
                with st.chat_message(msg["role"]):
                    st.markdown(msg["content"])
    
    user_input = st.chat_input("Escribe tu pregunta sobre Java...")
    
    if user_input:
        st.session_state.chat_messages.append({
            "role": "user",
            "content": user_input
        })
        
        with st.spinner("Claude está pensando..."):
            try:
                response = chat_with_claude(
                    st.session_state.chat_messages,
                    st.session_state.code
                )
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
