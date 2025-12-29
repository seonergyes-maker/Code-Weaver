import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;

/**
 * Cliente API para PLC COJER
 * Consulta periodica a endpoint de apilado
 */
public class CojerAPIClient {
    
    private String endpoint = "http://localhost:8080";
    private String token = "";
    private String ip = "";
    private String trabajo = "";
    private int pollingInterval = 1000;
    
    private volatile boolean polling = false;
    private Thread pollingThread;
    private List<ItemData> currentItems = new ArrayList<>();
    private APIListener listener;
    
    /** Datos de un item */
    public static class ItemData {
        public int orden;
        public String tag;
        public String codigo;
        public String descripcion;
        public int tipoEmbalaje;
        public String descTipoEmbalaje;
        public int ancho;
        public int largo;
        public String unidadMedida;
        public String variante;
    }
    
    /** Listener de eventos */
    public interface APIListener {
        void onItemsReceived(List<ItemData> items);
        void onError(String message);
        void onBajaSuccess(String tag);
        void onBajaError(String tag, String error);
    }
    
    public CojerAPIClient() {
        // Deshabilitar verificacion SSL para desarrollo
        disableSSLVerification();
    }
    
    /** Deshabilita la verificacion SSL (solo para desarrollo) */
    private void disableSSLVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (Exception e) {
            System.err.println("[API] Error configurando SSL: " + e.getMessage());
        }
    }
    
    // Setters
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public void setToken(String token) { this.token = token; }
    public void setIP(String ip) { this.ip = ip; }
    public void setTrabajo(String trabajo) { this.trabajo = trabajo; }
    public void setPollingInterval(int interval) { this.pollingInterval = interval; }
    public void setListener(APIListener listener) { this.listener = listener; }
    
    /** Inicia el polling periodico */
    public void startPolling() {
        if (polling) return;
        
        polling = true;
        pollingThread = new Thread(() -> {
            System.out.println("[API] Iniciando polling cada " + pollingInterval + "ms");
            System.out.println("[API] Endpoint: " + endpoint + "/rfid_lecturas/apilado");
            
            while (polling) {
                try {
                    fetchItems();
                    Thread.sleep(pollingInterval);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        pollingThread.setDaemon(true);
        pollingThread.start();
    }
    
    /** Detiene el polling */
    public void stopPolling() {
        polling = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
        System.out.println("[API] Polling detenido");
    }
    
    /** Obtiene los items de la API */
    private void fetchItems() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint + "/rfid_lecturas/apilado");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("api-token", token);
            conn.setRequestProperty("api-ip", ip);
            conn.setRequestProperty("api-trabajo", trabajo);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            
            // Enviar body vacio
            try (OutputStream os = conn.getOutputStream()) {
                os.write("{}".getBytes("UTF-8"));
                os.flush();
            }
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                parseResponse(response.toString());
            } else {
                String errorMsg = "HTTP " + responseCode;
                System.out.println("[API] Error: " + errorMsg);
                if (listener != null) {
                    listener.onError(errorMsg);
                }
            }
            
        } catch (Exception e) {
            System.out.println("[API] Error obteniendo items: " + e.getMessage());
            LogManager.getInstance().logError("API", e.getMessage());
            if (listener != null) {
                listener.onError(e.getMessage());
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    /** Parsea la respuesta JSON */
    private void parseResponse(String json) {
        try {
            currentItems.clear();
            
            // Verificar si hay items
            if (json.contains("\"items\":null") || json.contains("\"items\":[]")) {
                if (listener != null) {
                    listener.onItemsReceived(currentItems);
                }
                return;
            }
            
            // Buscar el array de items
            int itemsStart = json.indexOf("\"items\":");
            if (itemsStart == -1) {
                if (listener != null) {
                    listener.onItemsReceived(currentItems);
                }
                return;
            }
            
            // Encontrar el inicio del array
            int arrayStart = json.indexOf("[", itemsStart);
            if (arrayStart == -1) {
                if (listener != null) {
                    listener.onItemsReceived(currentItems);
                }
                return;
            }
            
            // Encontrar el fin del array
            int arrayEnd = findMatchingBracket(json, arrayStart);
            if (arrayEnd == -1) {
                if (listener != null) {
                    listener.onItemsReceived(currentItems);
                }
                return;
            }
            
            String itemsArray = json.substring(arrayStart + 1, arrayEnd);
            
            // Parsear cada objeto del array
            int pos = 0;
            while (pos < itemsArray.length()) {
                int objStart = itemsArray.indexOf("{", pos);
                if (objStart == -1) break;
                
                int objEnd = findMatchingBrace(itemsArray, objStart);
                if (objEnd == -1) break;
                
                String objStr = itemsArray.substring(objStart, objEnd + 1);
                ItemData item = parseItem(objStr);
                if (item != null) {
                    currentItems.add(item);
                }
                
                pos = objEnd + 1;
            }
            
            if (listener != null) {
                listener.onItemsReceived(currentItems);
            }
            
        } catch (Exception e) {
            System.out.println("[API] Error parseando respuesta: " + e.getMessage());
            LogManager.getInstance().logError("API", "Error parseando: " + e.getMessage());
        }
    }
    
    /** Encuentra el bracket de cierre correspondiente */
    private int findMatchingBracket(String s, int start) {
        int count = 0;
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '[') count++;
            else if (s.charAt(i) == ']') {
                count--;
                if (count == 0) return i;
            }
        }
        return -1;
    }
    
    /** Encuentra la llave de cierre correspondiente */
    private int findMatchingBrace(String s, int start) {
        int count = 0;
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i-1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == '{') count++;
                else if (c == '}') {
                    count--;
                    if (count == 0) return i;
                }
            }
        }
        return -1;
    }
    
    /** Parsea un item individual */
    private ItemData parseItem(String json) {
        try {
            ItemData item = new ItemData();
            
            item.orden = getIntValue(json, "orden");
            item.tag = getStringValue(json, "tag");
            item.codigo = getStringValue(json, "codigo");
            item.descripcion = getStringValue(json, "descripcion");
            item.tipoEmbalaje = getIntValue(json, "tipo_embalaje");
            item.descTipoEmbalaje = getStringValue(json, "desc_tipo_embalaje");
            item.ancho = getIntValue(json, "ancho");
            item.largo = getIntValue(json, "largo");
            item.unidadMedida = getStringValue(json, "unidad_medida");
            item.variante = getStringValue(json, "variante");
            
            return item;
        } catch (Exception e) {
            return null;
        }
    }
    
    /** Obtiene un valor string del JSON */
    private String getStringValue(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) return "";
        
        start += search.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        
        if (start >= json.length()) return "";
        
        if (json.charAt(start) == '"') {
            int end = json.indexOf("\"", start + 1);
            if (end == -1) return "";
            return json.substring(start + 1, end);
        } else if (json.substring(start).startsWith("null")) {
            return "";
        }
        
        return "";
    }
    
    /** Obtiene un valor int del JSON */
    private int getIntValue(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) return 0;
        
        start += search.length();
        while (start < json.length() && json.charAt(start) == ' ') start++;
        
        if (start >= json.length()) return 0;
        
        StringBuilder num = new StringBuilder();
        while (start < json.length()) {
            char c = json.charAt(start);
            if (Character.isDigit(c) || c == '-') {
                num.append(c);
                start++;
            } else {
                break;
            }
        }
        
        if (num.length() == 0) return 0;
        return Integer.parseInt(num.toString());
    }
    
    /** Obtiene el primer item de la lista */
    public ItemData getFirstItem() {
        if (currentItems.isEmpty()) return null;
        return currentItems.get(0);
    }
    
    /** Realiza la baja de un item */
    public void realizarBaja(String tag, int numero) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String url = String.format("%s/rfid_lecturas/baja_apilado/%s/%s/%s/%s/%d",
                    endpoint, token, ip, trabajo, tag, numero);
                
                System.out.println("[API] Baja: " + url);
                
                URL urlObj = new URL(url);
                conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 200 || responseCode == 204) {
                    System.out.println("[API] Baja exitosa: " + tag);
                    if (listener != null) {
                        listener.onBajaSuccess(tag);
                    }
                } else {
                    String error = "HTTP " + responseCode;
                    System.out.println("[API] Error en baja: " + error);
                    if (listener != null) {
                        listener.onBajaError(tag, error);
                    }
                }
                
            } catch (Exception e) {
                System.out.println("[API] Error en baja: " + e.getMessage());
                LogManager.getInstance().logError("API", "Error en baja: " + e.getMessage());
                if (listener != null) {
                    listener.onBajaError(tag, e.getMessage());
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
    
    /** Cierra el cliente */
    public void shutdown() {
        stopPolling();
    }
}
