package com.iotaccess.infrastructure.serial;

import com.fazecast.jSerialComm.SerialPort;
import com.iotaccess.domain.model.SerialPortInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Escáner de puertos seriales usando jSerialComm.
 */
@Component
@Slf4j
public class SerialPortScanner {

    /**
     * Obtiene la lista de puertos seriales disponibles en el sistema.
     * 
     * @return Lista de información de puertos
     */
    public List<SerialPortInfo> getAvailablePorts() {
        SerialPort[] ports = SerialPort.getCommPorts();

        log.info("Escaneando puertos seriales. Encontrados: {}", ports.length);

        return Arrays.stream(ports)
                .map(this::toSerialPortInfo)
                .collect(Collectors.toList());
    }

    /**
     * Busca un puerto por su nombre de sistema.
     * 
     * @param portName Nombre del puerto (ej: COM3)
     * @return SerialPort si existe, null si no
     */
    public SerialPort findPort(String portName) {
        SerialPort[] ports = SerialPort.getCommPorts();

        for (SerialPort port : ports) {
            if (port.getSystemPortName().equalsIgnoreCase(portName)) {
                return port;
            }
        }

        log.warn("Puerto no encontrado: {}", portName);
        return null;
    }

    /**
     * Verifica si un puerto específico está disponible.
     * 
     * @param portName Nombre del puerto
     * @return true si está disponible
     */
    public boolean isPortAvailable(String portName) {
        SerialPort port = findPort(portName);
        return port != null && !port.isOpen();
    }

    /**
     * Convierte un SerialPort de jSerialComm a nuestro modelo de dominio.
     */
    private SerialPortInfo toSerialPortInfo(SerialPort port) {
        return SerialPortInfo.builder()
                .systemPortName(port.getSystemPortName())
                .descriptivePortName(port.getDescriptivePortName())
                .isOpen(port.isOpen())
                .build();
    }
}
