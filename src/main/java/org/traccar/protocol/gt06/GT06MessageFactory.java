package org.traccar.protocol.gt06;

import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory for creating GT06 protocol messages
 */
@Slf4j
public class GT06MessageFactory {
    
    /**
     * Create a login message
     */
    public static ByteBuf createLoginMessage(String imei) {
        log.info("Creating login message for IMEI: {}", imei);
        return new GT06ProtocolEncoder.LoginMessage(imei, (short) 0, (short) 0).encode();
    }
    
    /**
     * Create a location message with current time
     */
    public static ByteBuf createLocationMessage(double latitude, double longitude, double speed, double course) {
        log.info("Creating location message: lat={}, lon={}, speed={}, course={}", 
                latitude, longitude, speed, course);
        return new GT06ProtocolEncoder.LocationMessage(latitude, longitude, speed, course).encode();
    }
    
    /**
     * Create a command message
     */
    public static ByteBuf createCommandMessage(String deviceId, String command) {
        log.info("Creating command message: {} for device: {}", command, deviceId);
        return new GT06ProtocolEncoder.CommandMessage(deviceId, command).encode();
    }
    
    // Common command shortcuts
    
    public static ByteBuf createPositionRequestMessage(String deviceId) {
        return createCommandMessage(deviceId, "WHERE#");
    }
    
    public static ByteBuf createResetMessage(String deviceId) {
        return createCommandMessage(deviceId, "RESET#");
    }
    
    public static ByteBuf createRebootMessage(String deviceId) {
        return createCommandMessage(deviceId, "REBOOT#");
    }
    
    public static ByteBuf createTimezoneMessage(String deviceId, int timezone) {
        return createCommandMessage(deviceId, "GMT," + timezone + "#");
    }
}
