import subprocess
import tempfile
import os
import shutil
import re
import zipfile
from pathlib import Path
from typing import Optional, Tuple
from dependency_manager import get_classpath, LIBS_DIR, install_detected_dependencies, detect_required_libraries


def auto_install_dependencies(files: dict) -> Tuple[bool, list, list]:
    """
    Automatically detect and install missing dependencies from code.
    
    Returns:
        Tuple of (success, installed_libs, error_messages)
    """
    result = install_detected_dependencies(files)
    installed = result.get('installed', [])
    errors = result.get('errors', [])
    return (result['success'], installed, errors)


def get_dependency_jars() -> list:
    """Returns list of all JAR files in the libs directory."""
    jars = []
    if os.path.exists(LIBS_DIR):
        for f in os.listdir(LIBS_DIR):
            if f.endswith('.jar'):
                jars.append(os.path.join(LIBS_DIR, f))
    return jars


def create_fat_jar(class_dir: str, jar_path: str, main_class: str) -> dict:
    """
    Creates a fat JAR that includes all compiled classes and dependency JARs.
    
    Args:
        class_dir: Directory containing compiled .class files
        jar_path: Output path for the JAR file
        main_class: The main class name for the manifest
    
    Returns:
        dict with success status and message
    """
    try:
        added_entries = set()
        
        with zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as jar:
            manifest_content = f"""Manifest-Version: 1.0
Main-Class: {main_class}
Created-By: Java IDE con Claude AI

"""
            jar.writestr('META-INF/MANIFEST.MF', manifest_content)
            added_entries.add('META-INF/MANIFEST.MF')
            added_entries.add('META-INF/')
            
            for root, dirs, files in os.walk(class_dir):
                for file in files:
                    if file.endswith('.class'):
                        file_path = os.path.join(root, file)
                        arcname = os.path.relpath(file_path, class_dir)
                        if arcname not in added_entries:
                            jar.write(file_path, arcname)
                            added_entries.add(arcname)
            
            dependency_jars = get_dependency_jars()
            libs_included = []
            
            for dep_jar in dependency_jars:
                try:
                    with zipfile.ZipFile(dep_jar, 'r') as dep:
                        for item in dep.namelist():
                            if item.startswith('META-INF/'):
                                if item.endswith('.SF') or item.endswith('.DSA') or item.endswith('.RSA'):
                                    continue
                                if item == 'META-INF/MANIFEST.MF':
                                    continue
                            
                            if item not in added_entries and not item.endswith('/'):
                                try:
                                    data = dep.read(item)
                                    jar.writestr(item, data)
                                    added_entries.add(item)
                                except Exception:
                                    pass
                    
                    libs_included.append(os.path.basename(dep_jar))
                except Exception as e:
                    pass
        
        if libs_included:
            return {
                'success': True,
                'libs_included': libs_included,
                'message': f'Fat JAR creado con {len(libs_included)} librerías incluidas'
            }
        else:
            return {
                'success': True,
                'libs_included': [],
                'message': 'JAR creado (sin dependencias externas)'
            }
            
    except Exception as e:
        return {
            'success': False,
            'error': str(e)
        }


def find_class_name(code: str) -> str:
    match = re.search(r'public\s+class\s+(\w+)', code)
    if match:
        return match.group(1)
    match = re.search(r'class\s+(\w+)', code)
    if match:
        return match.group(1)
    return "Main"


def find_all_classes(code: str) -> list:
    matches = re.findall(r'(?:public\s+)?class\s+(\w+)', code)
    return matches if matches else ["Main"]


def find_main_class(files: dict) -> Optional[str]:
    for filename, code in files.items():
        if 'public static void main' in code:
            class_name = find_class_name(code)
            return class_name
    return None


JAVA_TARGET_VERSION = "11"

def compile_java(code: str) -> dict:
    class_name = find_class_name(code)
    
    with tempfile.TemporaryDirectory() as temp_dir:
        java_file = os.path.join(temp_dir, f"{class_name}.java")
        with open(java_file, 'w', encoding='utf-8') as f:
            f.write(code)
        
        classpath = get_classpath()
        compile_cmd = ['javac', '-encoding', 'UTF-8', '--release', JAVA_TARGET_VERSION]
        if classpath:
            compile_cmd.extend(['-cp', classpath])
        compile_cmd.append(java_file)
        
        try:
            result = subprocess.run(
                compile_cmd,
                capture_output=True,
                text=True,
                timeout=30
            )
            
            if result.returncode != 0:
                return {
                    'success': False,
                    'error': result.stderr,
                    'class_name': class_name
                }
            
            class_file = os.path.join(temp_dir, f"{class_name}.class")
            if os.path.exists(class_file):
                return {
                    'success': True,
                    'message': f'Compilación exitosa: {class_name}.class generado',
                    'class_name': class_name,
                    'temp_dir': temp_dir,
                    'class_file': class_file
                }
            else:
                return {
                    'success': False,
                    'error': 'No se generó el archivo .class',
                    'class_name': class_name
                }
                
        except subprocess.TimeoutExpired:
            return {
                'success': False,
                'error': 'Tiempo de compilación agotado (30 segundos)',
                'class_name': class_name
            }
        except Exception as e:
            return {
                'success': False,
                'error': str(e),
                'class_name': class_name
            }


def create_jar(code: str, jar_name: str = None) -> dict:
    class_name = find_class_name(code)
    if jar_name is None:
        jar_name = f"{class_name}.jar"
    
    if not jar_name.endswith('.jar'):
        jar_name += '.jar'
    
    output_dir = os.path.join(os.getcwd(), 'output')
    os.makedirs(output_dir, exist_ok=True)
    
    with tempfile.TemporaryDirectory() as temp_dir:
        java_file = os.path.join(temp_dir, f"{class_name}.java")
        with open(java_file, 'w', encoding='utf-8') as f:
            f.write(code)
        
        classpath = get_classpath()
        compile_cmd = ['javac', '-encoding', 'UTF-8', '--release', JAVA_TARGET_VERSION]
        if classpath:
            compile_cmd.extend(['-cp', classpath])
        compile_cmd.append(java_file)
        
        try:
            compile_result = subprocess.run(
                compile_cmd,
                capture_output=True,
                text=True,
                timeout=30
            )
            
            if compile_result.returncode != 0:
                return {
                    'success': False,
                    'error': f'Error de compilación:\n{compile_result.stderr}',
                    'class_name': class_name
                }
            
            jar_path = os.path.join(output_dir, jar_name)
            
            fat_jar_result = create_fat_jar(temp_dir, jar_path, class_name)
            
            if not fat_jar_result['success']:
                return {
                    'success': False,
                    'error': f"Error al crear JAR: {fat_jar_result.get('error', 'Error desconocido')}",
                    'class_name': class_name
                }
            
            if os.path.exists(jar_path):
                libs_msg = ""
                if fat_jar_result.get('libs_included'):
                    libs_msg = f" (incluye: {', '.join(fat_jar_result['libs_included'])})"
                return {
                    'success': True,
                    'message': f'JAR creado exitosamente: {jar_name}{libs_msg}',
                    'jar_path': jar_path,
                    'jar_name': jar_name,
                    'class_name': class_name,
                    'libs_included': fat_jar_result.get('libs_included', [])
                }
            else:
                return {
                    'success': False,
                    'error': 'No se pudo crear el archivo JAR',
                    'class_name': class_name
                }
                
        except subprocess.TimeoutExpired:
            return {
                'success': False,
                'error': 'Tiempo de operación agotado (30 segundos)',
                'class_name': class_name
            }
        except Exception as e:
            return {
                'success': False,
                'error': str(e),
                'class_name': class_name
            }


def run_java(code: str) -> dict:
    class_name = find_class_name(code)
    
    with tempfile.TemporaryDirectory() as temp_dir:
        java_file = os.path.join(temp_dir, f"{class_name}.java")
        with open(java_file, 'w', encoding='utf-8') as f:
            f.write(code)
        
        classpath = get_classpath()
        compile_cmd = ['javac', '-encoding', 'UTF-8', '--release', JAVA_TARGET_VERSION]
        if classpath:
            compile_cmd.extend(['-cp', classpath])
        compile_cmd.append(java_file)
        
        try:
            compile_result = subprocess.run(
                compile_cmd,
                capture_output=True,
                text=True,
                timeout=30
            )
            
            if compile_result.returncode != 0:
                return {
                    'success': False,
                    'error': f'Error de compilación:\n{compile_result.stderr}',
                    'class_name': class_name
                }
            
            run_classpath = temp_dir
            if classpath:
                run_classpath = temp_dir + os.pathsep + classpath
            
            run_result = subprocess.run(
                ['java', '-Xmx128m', '-Xms32m', '-cp', run_classpath, class_name],
                capture_output=True,
                text=True,
                timeout=30
            )
            
            output = run_result.stdout
            if run_result.stderr:
                output += f'\n[stderr]\n{run_result.stderr}'
            
            return {
                'success': run_result.returncode == 0,
                'output': output if output else '(Sin salida)',
                'return_code': run_result.returncode,
                'class_name': class_name
            }
                
        except subprocess.TimeoutExpired:
            return {
                'success': False,
                'error': 'Tiempo de ejecución agotado (30 segundos)',
                'class_name': class_name
            }
        except Exception as e:
            return {
                'success': False,
                'error': str(e),
                'class_name': class_name
            }


def get_java_version() -> str:
    try:
        result = subprocess.run(
            ['java', '-version'],
            capture_output=True,
            text=True,
            timeout=10
        )
        version_info = result.stderr if result.stderr else result.stdout
        return version_info.split('\n')[0] if version_info else 'Java no disponible'
    except Exception as e:
        return f'Error al obtener versión: {e}'


def compile_multi_file(files: dict) -> dict:
    if not files:
        return {'success': False, 'error': 'No hay archivos para compilar'}
    
    with tempfile.TemporaryDirectory() as temp_dir:
        java_files = []
        for filename, code in files.items():
            if not filename.endswith('.java'):
                filename = f"{filename}.java"
            file_path = os.path.join(temp_dir, filename)
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(code)
            java_files.append(file_path)
        
        classpath = get_classpath()
        compile_cmd = ['javac', '-encoding', 'UTF-8', '--release', JAVA_TARGET_VERSION]
        if classpath:
            compile_cmd.extend(['-cp', classpath])
        compile_cmd.extend(java_files)
        
        try:
            result = subprocess.run(
                compile_cmd,
                capture_output=True,
                text=True,
                timeout=60
            )
            
            if result.returncode != 0:
                return {
                    'success': False,
                    'error': result.stderr
                }
            
            class_files = list(Path(temp_dir).glob('*.class'))
            return {
                'success': True,
                'message': f'Compilación exitosa: {len(class_files)} archivos .class generados',
                'class_count': len(class_files),
                'temp_dir': temp_dir
            }
                
        except subprocess.TimeoutExpired:
            return {
                'success': False,
                'error': 'Tiempo de compilación agotado (60 segundos)'
            }
        except Exception as e:
            return {
                'success': False,
                'error': str(e)
            }


def run_multi_file(files: dict, main_class: Optional[str] = None) -> dict:
    if not files:
        return {'success': False, 'error': 'No hay archivos para ejecutar'}
    
    if main_class is None:
        main_class = find_main_class(files)
    
    if main_class is None:
        return {
            'success': False,
            'error': 'No se encontró método main. Asegúrate de tener "public static void main(String[] args)" en alguna clase.'
        }
    
    with tempfile.TemporaryDirectory() as temp_dir:
        for filename, code in files.items():
            if not filename.endswith('.java'):
                filename = f"{filename}.java"
            file_path = os.path.join(temp_dir, filename)
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(code)
        
        java_files = [os.path.join(temp_dir, f) for f in os.listdir(temp_dir) if f.endswith('.java')]
        
        classpath = get_classpath()
        compile_cmd = ['javac', '-encoding', 'UTF-8', '--release', JAVA_TARGET_VERSION]
        if classpath:
            compile_cmd.extend(['-cp', classpath])
        compile_cmd.extend(java_files)
        
        try:
            compile_result = subprocess.run(
                compile_cmd,
                capture_output=True,
                text=True,
                timeout=60
            )
            
            if compile_result.returncode != 0:
                return {
                    'success': False,
                    'error': f'Error de compilación:\n{compile_result.stderr}',
                    'main_class': main_class
                }
            
            run_classpath = temp_dir
            if classpath:
                run_classpath = temp_dir + os.pathsep + classpath
            
            run_result = subprocess.run(
                ['java', '-Xmx128m', '-Xms32m', '-cp', run_classpath, main_class],
                capture_output=True,
                text=True,
                timeout=30
            )
            
            output = run_result.stdout
            if run_result.stderr:
                output += f'\n[stderr]\n{run_result.stderr}'
            
            return {
                'success': run_result.returncode == 0,
                'output': output if output else '(Sin salida)',
                'return_code': run_result.returncode,
                'main_class': main_class
            }
                
        except subprocess.TimeoutExpired:
            return {
                'success': False,
                'error': 'Tiempo de ejecución agotado (30 segundos)',
                'main_class': main_class
            }
        except Exception as e:
            return {
                'success': False,
                'error': str(e),
                'main_class': main_class
            }


def create_multi_jar(files: dict, jar_name: Optional[str] = None, main_class: Optional[str] = None) -> dict:
    if not files:
        return {'success': False, 'error': 'No hay archivos para empaquetar'}
    
    if main_class is None:
        main_class = find_main_class(files)
    
    if main_class is None:
        return {
            'success': False,
            'error': 'No se encontró método main para el JAR ejecutable.'
        }
    
    if jar_name is None:
        jar_name = f"{main_class}.jar"
    
    if not jar_name.endswith('.jar'):
        jar_name += '.jar'
    
    output_dir = os.path.join(os.getcwd(), 'output')
    os.makedirs(output_dir, exist_ok=True)
    
    with tempfile.TemporaryDirectory() as temp_dir:
        for filename, code in files.items():
            if not filename.endswith('.java'):
                filename = f"{filename}.java"
            file_path = os.path.join(temp_dir, filename)
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(code)
        
        java_files = [os.path.join(temp_dir, f) for f in os.listdir(temp_dir) if f.endswith('.java')]
        
        classpath = get_classpath()
        compile_cmd = ['javac', '-encoding', 'UTF-8', '--release', JAVA_TARGET_VERSION]
        if classpath:
            compile_cmd.extend(['-cp', classpath])
        compile_cmd.extend(java_files)
        
        try:
            compile_result = subprocess.run(
                compile_cmd,
                capture_output=True,
                text=True,
                timeout=60
            )
            
            if compile_result.returncode != 0:
                return {
                    'success': False,
                    'error': f'Error de compilación:\n{compile_result.stderr}',
                    'main_class': main_class
                }
            
            jar_path = os.path.join(output_dir, jar_name)
            
            class_files = [f for f in os.listdir(temp_dir) if f.endswith('.class')]
            
            fat_jar_result = create_fat_jar(temp_dir, jar_path, main_class)
            
            if not fat_jar_result['success']:
                return {
                    'success': False,
                    'error': f"Error al crear JAR: {fat_jar_result.get('error', 'Error desconocido')}",
                    'main_class': main_class
                }
            
            if os.path.exists(jar_path):
                libs_msg = ""
                libs_included = fat_jar_result.get('libs_included', [])
                if libs_included:
                    libs_msg = f" + {len(libs_included)} librerías"
                return {
                    'success': True,
                    'message': f'JAR creado exitosamente: {jar_name} ({len(class_files)} clases{libs_msg})',
                    'jar_path': jar_path,
                    'jar_name': jar_name,
                    'main_class': main_class,
                    'libs_included': libs_included
                }
            else:
                return {
                    'success': False,
                    'error': 'No se pudo crear el archivo JAR',
                    'main_class': main_class
                }
                
        except subprocess.TimeoutExpired:
            return {
                'success': False,
                'error': 'Tiempo de operación agotado',
                'main_class': main_class
            }
        except Exception as e:
            return {
                'success': False,
                'error': str(e),
                'main_class': main_class
            }
