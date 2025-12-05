import subprocess
import tempfile
import os
import shutil
import re
from pathlib import Path


def find_class_name(code: str) -> str:
    match = re.search(r'public\s+class\s+(\w+)', code)
    if match:
        return match.group(1)
    match = re.search(r'class\s+(\w+)', code)
    if match:
        return match.group(1)
    return "Main"


def compile_java(code: str) -> dict:
    class_name = find_class_name(code)
    
    with tempfile.TemporaryDirectory() as temp_dir:
        java_file = os.path.join(temp_dir, f"{class_name}.java")
        with open(java_file, 'w', encoding='utf-8') as f:
            f.write(code)
        
        try:
            result = subprocess.run(
                ['javac', '-encoding', 'UTF-8', java_file],
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
            
            run_result = subprocess.run(
                ['java', '-Xmx128m', '-Xms32m', '-cp', temp_dir, class_name],
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
