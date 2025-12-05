import subprocess
import tempfile
import os
import shutil
import re
from pathlib import Path
from typing import Optional
from dependency_manager import get_classpath, LIBS_DIR


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


def compile_java(code: str) -> dict:
    class_name = find_class_name(code)
    
    with tempfile.TemporaryDirectory() as temp_dir:
        java_file = os.path.join(temp_dir, f"{class_name}.java")
        with open(java_file, 'w', encoding='utf-8') as f:
            f.write(code)
        
        classpath = get_classpath()
        compile_cmd = ['javac', '-encoding', 'UTF-8']
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
        
        try:
            compile_result = subprocess.run(
                ['javac', '-encoding', 'UTF-8', java_file],
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
            
            manifest_file = os.path.join(temp_dir, 'MANIFEST.MF')
            with open(manifest_file, 'w') as f:
                f.write(f'Manifest-Version: 1.0\n')
                f.write(f'Main-Class: {class_name}\n')
                f.write('\n')
            
            jar_path = os.path.join(output_dir, jar_name)
            
            jar_result = subprocess.run(
                ['jar', 'cfm', jar_path, manifest_file, f'{class_name}.class'],
                capture_output=True,
                text=True,
                timeout=30,
                cwd=temp_dir
            )
            
            if jar_result.returncode != 0:
                return {
                    'success': False,
                    'error': f'Error al crear JAR:\n{jar_result.stderr}',
                    'class_name': class_name
                }
            
            if os.path.exists(jar_path):
                return {
                    'success': True,
                    'message': f'JAR creado exitosamente: {jar_name}',
                    'jar_path': jar_path,
                    'jar_name': jar_name,
                    'class_name': class_name
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
        compile_cmd = ['javac', '-encoding', 'UTF-8']
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
        compile_cmd = ['javac', '-encoding', 'UTF-8']
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
        compile_cmd = ['javac', '-encoding', 'UTF-8']
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
        compile_cmd = ['javac', '-encoding', 'UTF-8']
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
            
            manifest_file = os.path.join(temp_dir, 'MANIFEST.MF')
            with open(manifest_file, 'w') as f:
                f.write('Manifest-Version: 1.0\n')
                f.write(f'Main-Class: {main_class}\n')
                f.write('\n')
            
            jar_path = os.path.join(output_dir, jar_name)
            
            class_files = [f for f in os.listdir(temp_dir) if f.endswith('.class')]
            
            jar_result = subprocess.run(
                ['jar', 'cfm', jar_path, manifest_file] + class_files,
                capture_output=True,
                text=True,
                timeout=30,
                cwd=temp_dir
            )
            
            if jar_result.returncode != 0:
                return {
                    'success': False,
                    'error': f'Error al crear JAR:\n{jar_result.stderr}',
                    'main_class': main_class
                }
            
            if os.path.exists(jar_path):
                return {
                    'success': True,
                    'message': f'JAR creado exitosamente: {jar_name} ({len(class_files)} clases)',
                    'jar_path': jar_path,
                    'jar_name': jar_name,
                    'main_class': main_class
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
