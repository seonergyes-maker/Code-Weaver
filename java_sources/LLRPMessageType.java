/**
 * Enumeración de tipos de mensaje LLRP según la especificación EPCglobal LLRP.
 * Define los IDs de mensaje para comunicación con lectores RFID Zebra FX7500.
 * 
 * @author Sistema RFID
 * @version 1.0
 */
public enum LLRPMessageType {
    
    // Mensajes de capacidades del lector
    GET_READER_CAPABILITIES(1),
    GET_READER_CAPABILITIES_RESPONSE(11),
    
    // Mensajes de configuración del lector
    SET_READER_CONFIG(3),
    SET_READER_CONFIG_RESPONSE(13),
    GET_READER_CONFIG(2),
    GET_READER_CONFIG_RESPONSE(12),
    
    // Mensajes de ROSpec (Reader Operation Specification)
    ADD_ROSPEC(20),
    ADD_ROSPEC_RESPONSE(30),
    DELETE_ROSPEC(21),
    DELETE_ROSPEC_RESPONSE(31),
    START_ROSPEC(22),
    START_ROSPEC_RESPONSE(32),
    STOP_ROSPEC(23),
    STOP_ROSPEC_RESPONSE(33),
    ENABLE_ROSPEC(24),
    ENABLE_ROSPEC_RESPONSE(34),
    DISABLE_ROSPEC(25),
    DISABLE_ROSPEC_RESPONSE(35),
    GET_ROSPECS(26),
    GET_ROSPECS_RESPONSE(36),
    
    // Mensajes de AccessSpec
    ADD_ACCESSSPEC(40),
    ADD_ACCESSSPEC_RESPONSE(50),
    DELETE_ACCESSSPEC(41),
    DELETE_ACCESSSPEC_RESPONSE(51),
    ENABLE_ACCESSSPEC(42),
    ENABLE_ACCESSSPEC_RESPONSE(52),
    DISABLE_ACCESSSPEC(43),
    DISABLE_ACCESSSPEC_RESPONSE(53),
    GET_ACCESSSPECS(44),
    GET_ACCESSSPECS_RESPONSE(54),
    
    // Mensajes de reporte de acceso
    RO_ACCESS_REPORT(61),
    
    // Mensajes de keep-alive
    KEEPALIVE(62),
    KEEPALIVE_ACK(72),
    
    // Mensajes de notificación de eventos del lector
    READER_EVENT_NOTIFICATION(63),
    
    // Mensajes de error
    ERROR_MESSAGE(100),
    
    // Mensajes de conexión
    CLOSE_CONNECTION(14),
    CLOSE_CONNECTION_RESPONSE(4),
    
    // Mensajes de extensión de vendedor (Zebra)
    CUSTOM_MESSAGE(1023),
    
    // Mensaje desconocido
    UNKNOWN(-1);
    
    private final int typeId;
    
    /**
     * Constructor del tipo de mensaje.
     * 
     * @param typeId Identificador numérico del tipo de mensaje LLRP
     */
    LLRPMessageType(int typeId) {
        this.typeId = typeId;
    }
    
    /**
     * Obtiene el identificador numérico del tipo de mensaje.
     * 
     * @return ID del tipo de mensaje según especificación LLRP
     */
    public int getTypeId() {
        return typeId;
    }
    
    /**
     * Busca un tipo de mensaje por su ID numérico.
     * 
     * @param typeId ID del tipo de mensaje a buscar
     * @return El tipo de mensaje correspondiente o UNKNOWN si no se encuentra
     */
    public static LLRPMessageType fromTypeId(int typeId) {
        for (LLRPMessageType type : values()) {
            if (type.typeId == typeId) {
                return type;
            }
        }
        return UNKNOWN;
    }
    
    /**
     * Verifica si este tipo de mensaje es una respuesta.
     * 
     * @return true si es un mensaje de respuesta
     */
    public boolean isResponse() {
        return this.name().endsWith("_RESPONSE") || 
               this == RO_ACCESS_REPORT || 
               this == READER_EVENT_NOTIFICATION;
    }
    
    /**
     * Obtiene el tipo de respuesta esperado para este mensaje.
     * 
     * @return El tipo de mensaje de respuesta esperado o null si no aplica
     */
    public LLRPMessageType getExpectedResponse() {
        switch (this) {
            case GET_READER_CAPABILITIES:
                return GET_READER_CAPABILITIES_RESPONSE;
            case SET_READER_CONFIG:
                return SET_READER_CONFIG_RESPONSE;
            case GET_READER_CONFIG:
                return GET_READER_CONFIG_RESPONSE;
            case ADD_ROSPEC:
                return ADD_ROSPEC_RESPONSE;
            case DELETE_ROSPEC:
                return DELETE_ROSPEC_RESPONSE;
            case START_ROSPEC:
                return START_ROSPEC_RESPONSE;
            case STOP_ROSPEC:
                return STOP_ROSPEC_RESPONSE;
            case ENABLE_ROSPEC:
                return ENABLE_ROSPEC_RESPONSE;
            case DISABLE_ROSPEC:
                return DISABLE_ROSPEC_RESPONSE;
            case CLOSE_CONNECTION:
                return CLOSE_CONNECTION_RESPONSE;
            case KEEPALIVE:
                return KEEPALIVE_ACK;
            default:
                return null;
        }
    }
}
