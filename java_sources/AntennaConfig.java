import java.io.Serializable;

/**
 * Configuración de una antena individual para el lector RFID Zebra FX7500.
 * Permite configurar parámetros de potencia de transmisión, sensibilidad de recepción
 * y pérdida de cable para cálculos de EIRP.
 * 
 * @author Sistema RFID
 * @version 1.0
 */
public class AntennaConfig implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** Potencia de transmisión mínima permitida en dBm */
    public static final double MIN_TRANSMIT_POWER = 10.0;
    
    /** Potencia de transmisión máxima permitida en dBm */
    public static final double MAX_TRANSMIT_POWER = 30.0;
    
    /** Límite EIRP máximo según regulación FCC (Estados Unidos) en dBm */
    public static final double MAX_EIRP_FCC = 36.0;
    
    /** Límite EIRP máximo según regulación ETSI (Europa) en dBm */
    public static final double MAX_EIRP_ETSI = 33.0;
    
    /** Número de puerto de antena (1-4) */
    private int antennaPort;
    
    /** Potencia de transmisión en dBm */
    private double transmitPower;
    
    /** Índice de sensibilidad de recepción (0-n, depende del lector) */
    private int receiveSensitivity;
    
    /** Indica si la antena está habilitada */
    private boolean enabled;
    
    /** Pérdida de cable en dB para cálculo de EIRP */
    private double cableLoss;
    
    /** Ganancia de antena en dBi (típicamente 6.0 para antenas Zebra) */
    private double antennaGain;
    
    /** Umbral RSSI mínimo en dBm (-80 a 0). Tags con RSSI menor se ignoran. */
    private int rssiThreshold;
    
    /** Habilitar filtrado por RSSI para esta antena */
    private boolean rssiFilterEnabled;
    
    /** Indica si la antena esta fisicamente conectada al lector (detectado por hardware) */
    private transient boolean physicallyConnected = false;
    
    /**
     * Constructor por defecto. Crea una configuración con valores predeterminados.
     */
    public AntennaConfig() {
        this.antennaPort = 1;
        this.transmitPower = 25.0;
        this.receiveSensitivity = 0;
        this.enabled = true;
        this.cableLoss = 0.0;
        this.antennaGain = 6.0;
        this.rssiThreshold = -70;
        this.rssiFilterEnabled = false;
    }
    
    /**
     * Constructor con puerto de antena específico.
     * 
     * @param antennaPort Número de puerto de antena (1-4)
     * @throws IllegalArgumentException si el puerto está fuera de rango
     */
    public AntennaConfig(int antennaPort) {
        this();
        setAntennaPort(antennaPort);
    }
    
    /**
     * Constructor completo con todos los parámetros.
     * 
     * @param antennaPort Número de puerto de antena (1-4)
     * @param transmitPower Potencia de transmisión en dBm (10.0-30.0)
     * @param receiveSensitivity Índice de sensibilidad de recepción
     * @param enabled Si la antena está habilitada
     * @param cableLoss Pérdida de cable en dB
     */
    public AntennaConfig(int antennaPort, double transmitPower, 
                         int receiveSensitivity, boolean enabled, double cableLoss) {
        setAntennaPort(antennaPort);
        setTransmitPower(transmitPower);
        setReceiveSensitivity(receiveSensitivity);
        this.enabled = enabled;
        setCableLoss(cableLoss);
        this.antennaGain = 6.0;
    }
    
    /**
     * Obtiene el número de puerto de antena.
     * 
     * @return Número de puerto (1-4)
     */
    public int getAntennaPort() {
        return antennaPort;
    }
    
    /**
     * Establece el número de puerto de antena.
     * 
     * @param antennaPort Número de puerto (1-4)
     * @throws IllegalArgumentException si el puerto está fuera de rango
     */
    public void setAntennaPort(int antennaPort) {
        if (antennaPort < 1 || antennaPort > 4) {
            throw new IllegalArgumentException(
                "El puerto de antena debe estar entre 1 y 4. Valor recibido: " + antennaPort);
        }
        this.antennaPort = antennaPort;
    }
    
    /**
     * Obtiene la potencia de transmisión en dBm.
     * 
     * @return Potencia de transmisión
     */
    public double getTransmitPower() {
        return transmitPower;
    }
    
    /**
     * Establece la potencia de transmisión en dBm.
     * 
     * @param transmitPower Potencia de transmisión (10.0-30.0 dBm)
     * @throws IllegalArgumentException si la potencia está fuera de rango
     */
    public void setTransmitPower(double transmitPower) {
        if (transmitPower < MIN_TRANSMIT_POWER || transmitPower > MAX_TRANSMIT_POWER) {
            throw new IllegalArgumentException(
                String.format("La potencia de transmisión debe estar entre %.1f y %.1f dBm. Valor: %.1f",
                    MIN_TRANSMIT_POWER, MAX_TRANSMIT_POWER, transmitPower));
        }
        this.transmitPower = transmitPower;
    }
    
    /**
     * Obtiene el índice de sensibilidad de recepción.
     * 
     * @return Índice de sensibilidad (menor valor = mayor sensibilidad)
     */
    public int getReceiveSensitivity() {
        return receiveSensitivity;
    }
    
    /**
     * Establece el índice de sensibilidad de recepción.
     * 
     * @param receiveSensitivity Índice de sensibilidad (>= 0)
     * @throws IllegalArgumentException si el índice es negativo
     */
    public void setReceiveSensitivity(int receiveSensitivity) {
        if (receiveSensitivity < 0) {
            throw new IllegalArgumentException(
                "El índice de sensibilidad no puede ser negativo: " + receiveSensitivity);
        }
        this.receiveSensitivity = receiveSensitivity;
    }
    
    /**
     * Verifica si la antena está habilitada.
     * 
     * @return true si la antena está habilitada
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Habilita o deshabilita la antena.
     * 
     * @param enabled true para habilitar, false para deshabilitar
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Obtiene la pérdida de cable en dB.
     * 
     * @return Pérdida de cable
     */
    public double getCableLoss() {
        return cableLoss;
    }
    
    /**
     * Establece la pérdida de cable en dB.
     * 
     * @param cableLoss Pérdida de cable (>= 0)
     * @throws IllegalArgumentException si la pérdida es negativa
     */
    public void setCableLoss(double cableLoss) {
        if (cableLoss < 0) {
            throw new IllegalArgumentException(
                "La pérdida de cable no puede ser negativa: " + cableLoss);
        }
        this.cableLoss = cableLoss;
    }
    
    /**
     * Obtiene la ganancia de antena en dBi.
     * 
     * @return Ganancia de antena
     */
    public double getAntennaGain() {
        return antennaGain;
    }
    
    /**
     * Establece la ganancia de antena en dBi.
     * 
     * @param antennaGain Ganancia de antena
     */
    public void setAntennaGain(double antennaGain) {
        this.antennaGain = antennaGain;
    }
    
    /**
     * Obtiene el umbral RSSI mínimo para esta antena.
     * 
     * @return Umbral RSSI en dBm (-80 a 0)
     */
    public int getRssiThreshold() {
        return rssiThreshold;
    }
    
    /**
     * Establece el umbral RSSI mínimo. Tags con RSSI menor serán ignorados.
     * 
     * @param rssiThreshold Umbral RSSI en dBm (-80 a 0)
     */
    public void setRssiThreshold(int rssiThreshold) {
        if (rssiThreshold < -80) rssiThreshold = -80;
        if (rssiThreshold > 0) rssiThreshold = 0;
        this.rssiThreshold = rssiThreshold;
    }
    
    /**
     * Verifica si el filtrado RSSI está habilitado para esta antena.
     * 
     * @return true si el filtrado RSSI está activo
     */
    public boolean isRssiFilterEnabled() {
        return rssiFilterEnabled;
    }
    
    /**
     * Habilita o deshabilita el filtrado RSSI para esta antena.
     * 
     * @param enabled true para habilitar filtrado RSSI
     */
    public void setRssiFilterEnabled(boolean enabled) {
        this.rssiFilterEnabled = enabled;
    }
    
    /**
     * Verifica si la antena esta fisicamente conectada al lector.
     * Este valor es detectado por el hardware y no se persiste.
     * 
     * @return true si la antena esta fisicamente conectada
     */
    public boolean isPhysicallyConnected() {
        return physicallyConnected;
    }
    
    /**
     * Establece si la antena esta fisicamente conectada.
     * Este metodo es llamado por ZebraSDKConnection al leer el estado del lector.
     * 
     * @param connected true si la antena esta conectada fisicamente
     */
    public void setPhysicallyConnected(boolean connected) {
        this.physicallyConnected = connected;
    }
    
    /**
     * Calcula el EIRP (Equivalent Isotropically Radiated Power) efectivo.
     * EIRP = Potencia TX - Pérdida cable + Ganancia antena
     * 
     * @return EIRP en dBm
     */
    public double calculateEIRP() {
        return transmitPower - cableLoss + antennaGain;
    }
    
    /**
     * Valida la configuración contra límites regulatorios FCC.
     * 
     * @return true si cumple con regulación FCC
     */
    public boolean validateFCC() {
        return calculateEIRP() <= MAX_EIRP_FCC;
    }
    
    /**
     * Valida la configuración contra límites regulatorios ETSI.
     * 
     * @return true si cumple con regulación ETSI
     */
    public boolean validateETSI() {
        return calculateEIRP() <= MAX_EIRP_ETSI;
    }
    
    /**
     * Calcula la potencia máxima de transmisión permitida según regulación FCC.
     * 
     * @return Potencia máxima de transmisión en dBm
     */
    public double getMaxAllowedPowerFCC() {
        return Math.min(MAX_TRANSMIT_POWER, MAX_EIRP_FCC + cableLoss - antennaGain);
    }
    
    /**
     * Calcula la potencia máxima de transmisión permitida según regulación ETSI.
     * 
     * @return Potencia máxima de transmisión en dBm
     */
    public double getMaxAllowedPowerETSI() {
        return Math.min(MAX_TRANSMIT_POWER, MAX_EIRP_ETSI + cableLoss - antennaGain);
    }
    
    /**
     * Convierte la potencia de transmisión a índice para LLRP.
     * El FX7500 usa una tabla de potencia donde:
     * - Index 1 = 10.0 dBm
     * - Index 51 = 15.0 dBm
     * - Index 101 = 20.0 dBm
     * - Index 151 = 25.0 dBm
     * - Index 201 = 30.0 dBm (max)
     * Formula: index = (dBm - 10.0) * 10 + 1
     * 
     * @return Índice de potencia para protocolo LLRP (1-201)
     */
    public int getTransmitPowerIndex() {
        return (int) Math.round((transmitPower - MIN_TRANSMIT_POWER) * 10);
    }
    
    /**
     * Obtiene el índice de potencia para LLRP (alias para compatibilidad).
     * 
     * @return Índice de potencia para protocolo LLRP (1-201)
     */
    public int getPowerIndex() {
        return getTransmitPowerIndex();
    }
    
    /**
     * Establece la potencia de transmisión desde un índice LLRP.
     * Formula inversa: dBm = (index - 1) / 10.0 + 10.0
     * 
     * @param index Índice de potencia LLRP (1-201)
     */
    public void setTransmitPowerFromIndex(int index) {
        double power = (index / 10.0) + MIN_TRANSMIT_POWER;
        setTransmitPower(Math.min(Math.max(power, MIN_TRANSMIT_POWER), MAX_TRANSMIT_POWER));
    }
    
    /**
     * Crea una copia de esta configuración.
     * 
     * @return Nueva instancia con los mismos valores
     */
    public AntennaConfig copy() {
        AntennaConfig copy = new AntennaConfig();
        copy.antennaPort = this.antennaPort;
        copy.transmitPower = this.transmitPower;
        copy.receiveSensitivity = this.receiveSensitivity;
        copy.enabled = this.enabled;
        copy.cableLoss = this.cableLoss;
        copy.antennaGain = this.antennaGain;
        copy.rssiThreshold = this.rssiThreshold;
        copy.rssiFilterEnabled = this.rssiFilterEnabled;
        return copy;
    }
    
    @Override
    public String toString() {
        return String.format(
            "AntennaConfig[puerto=%d, potencia=%.1fdBm, sensibilidad=%d, " +
            "habilitada=%b, pérdidaCable=%.1fdB, EIRP=%.1fdBm]",
            antennaPort, transmitPower, receiveSensitivity, 
            enabled, cableLoss, calculateEIRP());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AntennaConfig other = (AntennaConfig) obj;
        return antennaPort == other.antennaPort &&
               Double.compare(other.transmitPower, transmitPower) == 0 &&
               receiveSensitivity == other.receiveSensitivity &&
               enabled == other.enabled &&
               Double.compare(other.cableLoss, cableLoss) == 0 &&
               Double.compare(other.antennaGain, antennaGain) == 0;
    }
    
    @Override
    public int hashCode() {
        int result = antennaPort;
        result = 31 * result + Double.hashCode(transmitPower);
        result = 31 * result + receiveSensitivity;
        result = 31 * result + (enabled ? 1 : 0);
        result = 31 * result + Double.hashCode(cableLoss);
        result = 31 * result + Double.hashCode(antennaGain);
        return result;
    }
}
