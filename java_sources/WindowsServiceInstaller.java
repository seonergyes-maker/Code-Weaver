import java.io.*;

/**
 * Generador de instalador para Windows Service.
 * Crea scripts batch y configuración para ejecutar el JAR como servicio de Windows
 * usando NSSM (Non-Sucking Service Manager) o WinSW.
 * 
 * @author RFID IDE
 * @version 1.0
 */
public class WindowsServiceInstaller {
    
    private String serviceName = "RFIDZebraService";
    private String displayName = "RFID Zebra FX7500 Service";
    private String description = "Servicio de lectura RFID para Zebra FX7500";
    private String jarFileName = "rfid-zebra.jar";
    private String installPath = "C:\\RFID";
    private String javaPath = "java";
    private String jvmOptions = "-Xmx256m";
    private boolean autoStart = true;
    
    /**
     * Genera todos los archivos necesarios para instalar como servicio de Windows.
     * 
     * @param outputDir Directorio donde generar los archivos
     * @throws IOException Si hay error al escribir archivos
     */
    public void generateAll(String outputDir) throws IOException {
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        generateNSSMInstaller(outputDir);
        generateNSSMUninstaller(outputDir);
        generateWinSWConfig(outputDir);
        generateWinSWInstaller(outputDir);
        generateStartBatch(outputDir);
        generateStopBatch(outputDir);
        generateReadme(outputDir);
    }
    
    /**
     * Genera script de instalación usando NSSM.
     */
    private void generateNSSMInstaller(String outputDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\r\n");
        sb.append(":: Instalador de servicio Windows usando NSSM\r\n");
        sb.append(":: NSSM: https://nssm.cc/download\r\n");
        sb.append(":: Ejecutar como Administrador\r\n\r\n");
        
        sb.append("echo ========================================\r\n");
        sb.append("echo   Instalador RFID Zebra FX7500\r\n");
        sb.append("echo ========================================\r\n");
        sb.append("echo.\r\n\r\n");
        
        sb.append(":: Verificar permisos de administrador\r\n");
        sb.append("net session >nul 2>&1\r\n");
        sb.append("if %errorLevel% neq 0 (\r\n");
        sb.append("    echo ERROR: Este script requiere permisos de Administrador.\r\n");
        sb.append("    echo Haz clic derecho y selecciona \"Ejecutar como administrador\"\r\n");
        sb.append("    pause\r\n");
        sb.append("    exit /b 1\r\n");
        sb.append(")\r\n\r\n");
        
        sb.append(":: Verificar Java\r\n");
        sb.append("where java >nul 2>&1\r\n");
        sb.append("if %errorLevel% neq 0 (\r\n");
        sb.append("    echo ERROR: Java no encontrado en el PATH.\r\n");
        sb.append("    echo Instala Java (OpenJDK 11+) desde: https://adoptium.net/\r\n");
        sb.append("    pause\r\n");
        sb.append("    exit /b 1\r\n");
        sb.append(")\r\n");
        sb.append("echo Java encontrado:\r\n");
        sb.append("java -version 2>&1 | findstr /i version\r\n\r\n");
        
        sb.append(":: Crear directorio de instalacion\r\n");
        sb.append("if not exist \"").append(installPath).append("\" (\r\n");
        sb.append("    mkdir \"").append(installPath).append("\"\r\n");
        sb.append("    echo Directorio creado: ").append(installPath).append("\r\n");
        sb.append(")\r\n\r\n");
        
        sb.append(":: Copiar archivos\r\n");
        sb.append("echo Copiando archivos...\r\n");
        sb.append("copy /Y \"").append(jarFileName).append("\" \"").append(installPath).append("\\\"\r\n");
        sb.append("if exist \"rfid_config.json\" copy /Y \"rfid_config.json\" \"").append(installPath).append("\\\"\r\n");
        sb.append("if exist \"libs\" xcopy /Y /E \"libs\" \"").append(installPath).append("\\libs\\\"\r\n\r\n");
        
        sb.append(":: Verificar NSSM\r\n");
        sb.append("where nssm >nul 2>&1\r\n");
        sb.append("if %errorLevel% neq 0 (\r\n");
        sb.append("    if exist \"nssm.exe\" (\r\n");
        sb.append("        set NSSM=nssm.exe\r\n");
        sb.append("    ) else (\r\n");
        sb.append("        echo ERROR: NSSM no encontrado.\r\n");
        sb.append("        echo Descarga NSSM de https://nssm.cc/download\r\n");
        sb.append("        echo y coloca nssm.exe en este directorio o en el PATH.\r\n");
        sb.append("        pause\r\n");
        sb.append("        exit /b 1\r\n");
        sb.append("    )\r\n");
        sb.append(") else (\r\n");
        sb.append("    set NSSM=nssm\r\n");
        sb.append(")\r\n\r\n");
        
        sb.append(":: Instalar servicio\r\n");
        sb.append("echo Instalando servicio...\r\n");
        sb.append("%NSSM% install ").append(serviceName).append(" \"").append(javaPath).append("\"\r\n");
        sb.append("%NSSM% set ").append(serviceName).append(" AppParameters \"")
          .append(jvmOptions).append(" -jar ").append(installPath).append("\\").append(jarFileName).append(" --headless --autostart\"\r\n");
        sb.append("%NSSM% set ").append(serviceName).append(" AppDirectory \"").append(installPath).append("\"\r\n");
        sb.append("%NSSM% set ").append(serviceName).append(" DisplayName \"").append(displayName).append("\"\r\n");
        sb.append("%NSSM% set ").append(serviceName).append(" Description \"").append(description).append("\"\r\n");
        sb.append("%NSSM% set ").append(serviceName).append(" Start ");
        sb.append(autoStart ? "SERVICE_AUTO_START" : "SERVICE_DEMAND_START").append("\r\n");
        sb.append("%NSSM% set ").append(serviceName).append(" AppStdout \"").append(installPath).append("\\logs\\stdout.log\"\r\n");
        sb.append("%NSSM% set ").append(serviceName).append(" AppStderr \"").append(installPath).append("\\logs\\stderr.log\"\r\n");
        sb.append("%NSSM% set ").append(serviceName).append(" AppRotateFiles 1\r\n");
        sb.append("%NSSM% set ").append(serviceName).append(" AppRotateBytes 10485760\r\n\r\n");
        
        sb.append(":: Crear directorio de logs\r\n");
        sb.append("if not exist \"").append(installPath).append("\\logs\" mkdir \"").append(installPath).append("\\logs\"\r\n\r\n");
        
        sb.append(":: Iniciar servicio\r\n");
        sb.append("echo Iniciando servicio...\r\n");
        sb.append("%NSSM% start ").append(serviceName).append("\r\n\r\n");
        
        sb.append("echo.\r\n");
        sb.append("echo ========================================\r\n");
        sb.append("echo   Instalacion completada!\r\n");
        sb.append("echo ========================================\r\n");
        sb.append("echo.\r\n");
        sb.append("echo El servicio \"").append(displayName).append("\" ha sido instalado.\r\n");
        sb.append("echo.\r\n");
        sb.append("echo Comandos utiles:\r\n");
        sb.append("echo   - Ver estado:    sc query ").append(serviceName).append("\r\n");
        sb.append("echo   - Detener:       net stop ").append(serviceName).append("\r\n");
        sb.append("echo   - Iniciar:       net start ").append(serviceName).append("\r\n");
        sb.append("echo   - Logs:          ").append(installPath).append("\\logs\\\r\n");
        sb.append("echo.\r\n");
        sb.append("pause\r\n");
        
        writeFile(outputDir + "/install-service-nssm.bat", sb.toString());
    }
    
    /**
     * Genera script de desinstalación NSSM.
     */
    private void generateNSSMUninstaller(String outputDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\r\n");
        sb.append(":: Desinstalador de servicio Windows\r\n");
        sb.append(":: Ejecutar como Administrador\r\n\r\n");
        
        sb.append("net session >nul 2>&1\r\n");
        sb.append("if %errorLevel% neq 0 (\r\n");
        sb.append("    echo ERROR: Requiere permisos de Administrador.\r\n");
        sb.append("    pause\r\n");
        sb.append("    exit /b 1\r\n");
        sb.append(")\r\n\r\n");
        
        sb.append("echo Deteniendo servicio...\r\n");
        sb.append("net stop ").append(serviceName).append(" 2>nul\r\n\r\n");
        
        sb.append("echo Eliminando servicio...\r\n");
        sb.append("nssm remove ").append(serviceName).append(" confirm\r\n\r\n");
        
        sb.append("echo.\r\n");
        sb.append("echo Servicio desinstalado.\r\n");
        sb.append("echo Los archivos en ").append(installPath).append(" NO han sido eliminados.\r\n");
        sb.append("pause\r\n");
        
        writeFile(outputDir + "/uninstall-service.bat", sb.toString());
    }
    
    /**
     * Genera configuración para WinSW (alternativa a NSSM).
     */
    private void generateWinSWConfig(String outputDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
        sb.append("<service>\r\n");
        sb.append("  <id>").append(serviceName).append("</id>\r\n");
        sb.append("  <name>").append(displayName).append("</name>\r\n");
        sb.append("  <description>").append(description).append("</description>\r\n");
        sb.append("  <executable>").append(javaPath).append("</executable>\r\n");
        sb.append("  <arguments>").append(jvmOptions).append(" -jar ").append(jarFileName).append(" --headless --autostart</arguments>\r\n");
        sb.append("  <workingdirectory>").append(installPath).append("</workingdirectory>\r\n");
        sb.append("  <logpath>").append(installPath).append("\\logs</logpath>\r\n");
        sb.append("  <log mode=\"roll-by-size\">\r\n");
        sb.append("    <sizeThreshold>10240</sizeThreshold>\r\n");
        sb.append("    <keepFiles>8</keepFiles>\r\n");
        sb.append("  </log>\r\n");
        sb.append("  <onfailure action=\"restart\" delay=\"10 sec\"/>\r\n");
        sb.append("  <onfailure action=\"restart\" delay=\"20 sec\"/>\r\n");
        sb.append("  <onfailure action=\"none\"/>\r\n");
        sb.append("  <resetfailure>1 hour</resetfailure>\r\n");
        if (autoStart) {
            sb.append("  <startmode>Automatic</startmode>\r\n");
        }
        sb.append("</service>\r\n");
        
        writeFile(outputDir + "/rfid-service.xml", sb.toString());
    }
    
    /**
     * Genera instalador WinSW.
     */
    private void generateWinSWInstaller(String outputDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\r\n");
        sb.append(":: Instalador usando WinSW\r\n");
        sb.append(":: WinSW: https://github.com/winsw/winsw/releases\r\n");
        sb.append(":: Renombra winsw.exe a rfid-service.exe\r\n\r\n");
        
        sb.append("net session >nul 2>&1\r\n");
        sb.append("if %errorLevel% neq 0 (\r\n");
        sb.append("    echo ERROR: Requiere permisos de Administrador.\r\n");
        sb.append("    pause\r\n");
        sb.append("    exit /b 1\r\n");
        sb.append(")\r\n\r\n");
        
        sb.append(":: Verificar Java\r\n");
        sb.append("where java >nul 2>&1\r\n");
        sb.append("if %errorLevel% neq 0 (\r\n");
        sb.append("    echo ERROR: Java no encontrado en el PATH.\r\n");
        sb.append("    echo Instala Java (OpenJDK 11+) desde: https://adoptium.net/\r\n");
        sb.append("    pause\r\n");
        sb.append("    exit /b 1\r\n");
        sb.append(")\r\n\r\n");
        
        sb.append("if not exist \"rfid-service.exe\" (\r\n");
        sb.append("    echo ERROR: rfid-service.exe no encontrado.\r\n");
        sb.append("    echo Descarga WinSW y renombralo a rfid-service.exe\r\n");
        sb.append("    pause\r\n");
        sb.append("    exit /b 1\r\n");
        sb.append(")\r\n\r\n");
        
        sb.append("if not exist \"").append(installPath).append("\" mkdir \"").append(installPath).append("\"\r\n");
        sb.append("if not exist \"").append(installPath).append("\\logs\" mkdir \"").append(installPath).append("\\logs\"\r\n\r\n");
        
        sb.append("copy /Y \"").append(jarFileName).append("\" \"").append(installPath).append("\\\"\r\n");
        sb.append("copy /Y \"rfid-service.exe\" \"").append(installPath).append("\\\"\r\n");
        sb.append("copy /Y \"rfid-service.xml\" \"").append(installPath).append("\\\"\r\n");
        sb.append("if exist \"rfid_config.json\" copy /Y \"rfid_config.json\" \"").append(installPath).append("\\\"\r\n");
        sb.append("if exist \"libs\" xcopy /Y /E \"libs\" \"").append(installPath).append("\\libs\\\"\r\n\r\n");
        
        sb.append("cd /d \"").append(installPath).append("\"\r\n");
        sb.append("rfid-service.exe install\r\n");
        sb.append("rfid-service.exe start\r\n\r\n");
        
        sb.append("echo Servicio instalado e iniciado.\r\n");
        sb.append("pause\r\n");
        
        writeFile(outputDir + "/install-service-winsw.bat", sb.toString());
    }
    
    /**
     * Genera script para iniciar manualmente.
     */
    private void generateStartBatch(String outputDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\r\n");
        sb.append(":: Inicia la aplicacion RFID manualmente (sin servicio)\r\n");
        sb.append("cd /d \"").append(installPath).append("\"\r\n");
        sb.append("java ").append(jvmOptions).append(" -jar ").append(jarFileName).append("\r\n");
        sb.append("pause\r\n");
        
        writeFile(outputDir + "/start-rfid.bat", sb.toString());
    }
    
    /**
     * Genera script para detener.
     */
    private void generateStopBatch(String outputDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\r\n");
        sb.append(":: Detiene el servicio RFID\r\n");
        sb.append("net stop ").append(serviceName).append("\r\n");
        sb.append("echo Servicio detenido.\r\n");
        sb.append("pause\r\n");
        
        writeFile(outputDir + "/stop-rfid.bat", sb.toString());
    }
    
    /**
     * Genera archivo README con instrucciones.
     */
    private void generateReadme(String outputDir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Instalación de RFID Zebra FX7500 como Servicio de Windows\r\n\r\n");
        
        sb.append("## Requisitos\r\n");
        sb.append("- Windows 7/10/11 o Windows Server\r\n");
        sb.append("- Java 11 o superior instalado\r\n");
        sb.append("- Permisos de Administrador\r\n\r\n");
        
        sb.append("## Opción 1: NSSM (Recomendado)\r\n");
        sb.append("1. Descarga NSSM de https://nssm.cc/download\r\n");
        sb.append("2. Coloca nssm.exe en esta carpeta o en el PATH\r\n");
        sb.append("3. Ejecuta `install-service-nssm.bat` como Administrador\r\n\r\n");
        
        sb.append("## Opción 2: WinSW\r\n");
        sb.append("1. Descarga WinSW de https://github.com/winsw/winsw/releases\r\n");
        sb.append("2. Renombra el .exe descargado a `rfid-service.exe`\r\n");
        sb.append("3. Ejecuta `install-service-winsw.bat` como Administrador\r\n\r\n");
        
        sb.append("## Comandos útiles\r\n");
        sb.append("```\r\n");
        sb.append("net start ").append(serviceName).append("   # Iniciar servicio\r\n");
        sb.append("net stop ").append(serviceName).append("    # Detener servicio\r\n");
        sb.append("sc query ").append(serviceName).append("    # Ver estado\r\n");
        sb.append("```\r\n\r\n");
        
        sb.append("## Logs\r\n");
        sb.append("Los logs se guardan en: ").append(installPath).append("\\logs\\\r\n\r\n");
        
        sb.append("## Configuración\r\n");
        sb.append("Edita `rfid_config.json` en ").append(installPath).append(" para cambiar:\r\n");
        sb.append("- Dirección IP del lector\r\n");
        sb.append("- Configuración de antenas\r\n");
        sb.append("- URL del API endpoint\r\n");
        
        writeFile(outputDir + "/LEEME-Windows.txt", sb.toString());
    }
    
    private void writeFile(String path, String content) throws IOException {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write(content);
        }
    }
    
    // Getters y Setters
    
    public String getServiceName() { return serviceName; }
    public void setServiceName(String name) { this.serviceName = name; }
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String name) { this.displayName = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String desc) { this.description = desc; }
    
    public String getJarFileName() { return jarFileName; }
    public void setJarFileName(String name) { this.jarFileName = name; }
    
    public String getInstallPath() { return installPath; }
    public void setInstallPath(String path) { this.installPath = path; }
    
    public String getJavaPath() { return javaPath; }
    public void setJavaPath(String path) { this.javaPath = path; }
    
    public String getJvmOptions() { return jvmOptions; }
    public void setJvmOptions(String options) { this.jvmOptions = options; }
    
    public boolean isAutoStart() { return autoStart; }
    public void setAutoStart(boolean auto) { this.autoStart = auto; }
}
