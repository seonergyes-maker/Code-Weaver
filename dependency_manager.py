import os
import requests
import json
from typing import Optional

LIBS_DIR = os.path.join(os.getcwd(), 'libs')

COMMON_LIBRARIES = {
    'gson': {
        'group': 'com.google.code.gson',
        'artifact': 'gson',
        'version': '2.10.1'
    },
    'commons-lang3': {
        'group': 'org.apache.commons',
        'artifact': 'commons-lang3',
        'version': '3.14.0'
    },
    'commons-io': {
        'group': 'commons-io',
        'artifact': 'commons-io',
        'version': '2.15.1'
    },
    'jackson-core': {
        'group': 'com.fasterxml.jackson.core',
        'artifact': 'jackson-core',
        'version': '2.16.1'
    },
    'jackson-databind': {
        'group': 'com.fasterxml.jackson.core',
        'artifact': 'jackson-databind',
        'version': '2.16.1'
    },
    'slf4j-api': {
        'group': 'org.slf4j',
        'artifact': 'slf4j-api',
        'version': '2.0.11'
    },
    'logback-classic': {
        'group': 'ch.qos.logback',
        'artifact': 'logback-classic',
        'version': '1.4.14'
    },
    'junit': {
        'group': 'junit',
        'artifact': 'junit',
        'version': '4.13.2'
    },
    'lombok': {
        'group': 'org.projectlombok',
        'artifact': 'lombok',
        'version': '1.18.30'
    },
    'guava': {
        'group': 'com.google.guava',
        'artifact': 'guava',
        'version': '33.0.0-jre'
    },
    'okhttp': {
        'group': 'com.squareup.okhttp3',
        'artifact': 'okhttp',
        'version': '4.12.0'
    },
    'httpclient': {
        'group': 'org.apache.httpcomponents.client5',
        'artifact': 'httpclient5',
        'version': '5.3'
    },
    'mysql-connector': {
        'group': 'com.mysql',
        'artifact': 'mysql-connector-j',
        'version': '8.3.0'
    },
    'postgresql': {
        'group': 'org.postgresql',
        'artifact': 'postgresql',
        'version': '42.7.1'
    },
    'sqlite-jdbc': {
        'group': 'org.xerial',
        'artifact': 'sqlite-jdbc',
        'version': '3.45.1.0'
    }
}


def ensure_libs_dir():
    os.makedirs(LIBS_DIR, exist_ok=True)
    return LIBS_DIR


def get_maven_url(group: str, artifact: str, version: str) -> str:
    group_path = group.replace('.', '/')
    return f"https://repo1.maven.org/maven2/{group_path}/{artifact}/{version}/{artifact}-{version}.jar"


def download_library(name: str) -> dict:
    name_lower = name.lower().strip()
    
    if name_lower in COMMON_LIBRARIES:
        lib_info = COMMON_LIBRARIES[name_lower]
        group = lib_info['group']
        artifact = lib_info['artifact']
        version = lib_info['version']
    else:
        return {
            'success': False,
            'error': f'Librería "{name}" no encontrada. Librerías disponibles: {", ".join(sorted(COMMON_LIBRARIES.keys()))}'
        }
    
    ensure_libs_dir()
    
    jar_filename = f"{artifact}-{version}.jar"
    jar_path = os.path.join(LIBS_DIR, jar_filename)
    
    if os.path.exists(jar_path):
        return {
            'success': True,
            'message': f'La librería {name} ya está instalada',
            'jar_path': jar_path,
            'jar_name': jar_filename
        }
    
    url = get_maven_url(group, artifact, version)
    
    try:
        response = requests.get(url, timeout=60, stream=True)
        
        if response.status_code == 200:
            with open(jar_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)
            
            return {
                'success': True,
                'message': f'Librería {artifact} v{version} instalada correctamente',
                'jar_path': jar_path,
                'jar_name': jar_filename
            }
        else:
            return {
                'success': False,
                'error': f'Error al descargar: HTTP {response.status_code}'
            }
    except requests.Timeout:
        return {
            'success': False,
            'error': 'Tiempo de descarga agotado'
        }
    except Exception as e:
        return {
            'success': False,
            'error': f'Error de descarga: {str(e)}'
        }


def download_custom_library(group: str, artifact: str, version: str) -> dict:
    ensure_libs_dir()
    
    jar_filename = f"{artifact}-{version}.jar"
    jar_path = os.path.join(LIBS_DIR, jar_filename)
    
    if os.path.exists(jar_path):
        return {
            'success': True,
            'message': f'La librería {artifact} ya está instalada',
            'jar_path': jar_path,
            'jar_name': jar_filename
        }
    
    url = get_maven_url(group, artifact, version)
    
    try:
        response = requests.get(url, timeout=60, stream=True)
        
        if response.status_code == 200:
            with open(jar_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)
            
            return {
                'success': True,
                'message': f'Librería {artifact} v{version} instalada correctamente',
                'jar_path': jar_path,
                'jar_name': jar_filename
            }
        else:
            return {
                'success': False,
                'error': f'Error al descargar desde Maven: HTTP {response.status_code}'
            }
    except Exception as e:
        return {
            'success': False,
            'error': f'Error de descarga: {str(e)}'
        }


def list_installed_libraries() -> list:
    if not os.path.exists(LIBS_DIR):
        return []
    
    jars = [f for f in os.listdir(LIBS_DIR) if f.endswith('.jar')]
    return sorted(jars)


def remove_library(jar_name: str) -> dict:
    jar_path = os.path.join(LIBS_DIR, jar_name)
    
    if not os.path.exists(jar_path):
        return {
            'success': False,
            'error': f'Librería {jar_name} no encontrada'
        }
    
    try:
        os.remove(jar_path)
        return {
            'success': True,
            'message': f'Librería {jar_name} eliminada'
        }
    except Exception as e:
        return {
            'success': False,
            'error': f'Error al eliminar: {str(e)}'
        }


def get_classpath() -> str:
    if not os.path.exists(LIBS_DIR):
        return ""
    
    jars = [os.path.join(LIBS_DIR, f) for f in os.listdir(LIBS_DIR) if f.endswith('.jar')]
    
    if not jars:
        return ""
    
    return os.pathsep.join(jars)


def get_available_libraries() -> dict:
    return COMMON_LIBRARIES
