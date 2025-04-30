package org.traccar.protocol.gt06;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.TimeZone;

@Slf4j
public class GT06ProtocolEncoder {

    private static final int MSG_COMMAND = 0x80;
    private static final int MSG_LOGIN = 0x01;
    private static final int MSG_GPS = 0x22;
    private static final int MSG_HEARTBEAT = 0x13;
    
    /**
     * Base class for GT06 protocol messages
     */
    @Data
    public static abstract class Message {
        private short header = (short)0x7878; // Default header
        
        /**
         * Encode this message to a ByteBuf
         */
        public ByteBuf encode() {
            ByteBuf buf = Unpooled.buffer();
            buf.writeShort(header);
            buf.writeByte(0); // Placeholder for length
            encodeContent(buf);
            int length = buf.writerIndex() - 2 - 1 + 2 + 2; // Set length, excluding header and length and add 2 for checksum and 2 for serial number
            buf.setByte(2, length); 
            buf.writeShort(0x00c0); // Placeholder for index
            buf.writeShort(Checksum.crc16(Checksum.CRC16_X25, buf.nioBuffer(2, buf.writerIndex() - 2)));
            buf.writeBytes(new byte[]{0x0D, 0x0A}); // delimiter
            return buf;
        }
        
        /**
         * Encode message-specific content
         */
        protected abstract void encodeContent(ByteBuf buf);
        
        /**
         * Calculate and write checksum
         */
        protected void writeChecksum(ByteBuf buf) {
            int checksum = 0;
            for (int i = 2; i < buf.writerIndex(); i++) {
                checksum ^= buf.getByte(i);
            }
            buf.writeShort(checksum);
        }
    }
    
    /**
     * Command message sent to the device
     */
    @Builder
    @AllArgsConstructor
    public static class CommandMessage extends Message {
        private String deviceId;
        private String commandData;
        
        @Override
        protected void encodeContent(ByteBuf buf) {
            byte[] data = commandData.getBytes(StandardCharsets.US_ASCII);
            int length = 4 + 1 + data.length + 2; // IMEI (8) / 2 + protocol (1) + data + checksum (2)
            
            buf.writeByte(length);         // Length
            buf.writeByte(MSG_COMMAND);    // Protocol
            
            // Write 8-byte IMEI
            for (int i = 0; i < 8; i++) {
                int index = i * 2;
                if (index < deviceId.length()) {
                    int b = Integer.parseInt(deviceId.substring(
                            index, 
                            index + Math.min(2, deviceId.length() - index)), 16);
                    buf.writeByte(b);
                } else {
                    buf.writeByte(0);
                }
            }
            
            buf.writeBytes(data); // Command data
        }
    }
    
    /**
     * Login message to identify the device
     */
    @Builder
    @AllArgsConstructor
    public static class LoginMessage extends Message {
        private String imei;
        private short modelIdentifier;
        private short timeZoneLanguage;
        
        @Override
        protected void encodeContent(ByteBuf buf) {
            buf.writeByte(MSG_LOGIN);  // Protocol (login)

            String paddedImei = String.format("%16s", imei).replace(' ', '0');
            for (int i = 0; i < 8; i++) {
                int startIndex = i * 2;
                buf.writeByte((byte) Integer.parseInt(paddedImei.substring(startIndex, startIndex + 2), 16));
            }

            buf.writeShort(modelIdentifier);
            buf.writeShort(timeZoneLanguage);
        }
    }
    
    /**
     * Location message with GPS coordinates
     */
    @Builder
    @AllArgsConstructor
    public static class LocationMessage extends Message {
        private static final double KNOTS_TO_KPH_RATIO = 0.539957;

        private double latitude;
        private double longitude;
        private double speed;
        private double course;
        private Calendar time;
        private short mcc; // mobile country code
        private byte mnc; // mobile network code
        private short lac; // location area code
        private int cellId; // cell ID
        private byte acc; // accuracy
        private byte uploadMode; // upload mode
        private byte realTimeUpload; // real-time upload
        private int mileage;



        public LocationMessage(double latitude, double longitude, double speed, double course) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.speed = speed;
            this.course = course;
            this.time = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        }
        
        @Override
        protected void encodeContent(ByteBuf buf) {
            buf.writeByte(MSG_GPS);  // Protocol (GPS)
            
            // Time (UTC)
            Calendar calendar = time != null ? time : 
                    Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            buf.writeByte(calendar.get(Calendar.YEAR) - 2000); // Years since 2000
            buf.writeByte(calendar.get(Calendar.MONTH) + 1);
            buf.writeByte(calendar.get(Calendar.DAY_OF_MONTH));
            buf.writeByte(calendar.get(Calendar.HOUR_OF_DAY));
            buf.writeByte(calendar.get(Calendar.MINUTE));
            buf.writeByte(calendar.get(Calendar.SECOND));
            
            // Satellite count
            buf.writeByte(0xcf);
            
            // Coordinates
            buf.writeInt((int) Math.abs(latitude * 60 * 30000));
            buf.writeInt((int) Math.abs(longitude * 60 * 30000));
            
            // Speed & course
            buf.writeByte((int) (speed / KNOTS_TO_KPH_RATIO));
            int temp = (int) course;
            temp = temp | 0x1400; // Set reserve bit
            buf.writeShort(temp);

            buf.writeShort(mcc);
            buf.writeByte(mnc);
            buf.writeShort(lac);
            buf.writeMedium(cellId);
            buf.writeByte(acc);
            buf.writeByte(uploadMode);
            buf.writeByte(realTimeUpload);
            buf.writeInt(mileage);
        }
    }
    
    /**
     * Heartbeat message sent by device to keep connection alive
     */
    @Builder
    @AllArgsConstructor
    public static class HeartbeatMessage extends Message {
        private String terminalInfo;
        private int voltage;
        private int gsm;
        
        @Override
        protected void encodeContent(ByteBuf buf) {
            buf.writeByte(MSG_HEARTBEAT);  // Protocol (heartbeat)
            
            // Terminal information (default 0)
            int info = terminalInfo != null ? Integer.parseInt(terminalInfo, 16) : 0;
            buf.writeByte(info);
            
            // Voltage level (0-255)
            buf.writeByte(Math.min(Math.max(voltage, 0), 255));
            
            // GSM signal strength (0-4)
            buf.writeByte(Math.min(Math.max(gsm, 0), 4));
            
            // Reserved bytes
            buf.writeShort(0);
        }
    }
    
    // Original API methods for backward compatibility
    
    public ByteBuf encodeCommand(String uniqueId, String command) {
        return new CommandMessage(uniqueId, command).encode();
    }
    
    public ByteBuf requestLocation(String uniqueId) {
        return encodeCommand(uniqueId, "WHERE#");
    }
    
    public ByteBuf requestReset(String uniqueId) {
        return encodeCommand(uniqueId, "RESET#");
    }
    
    public ByteBuf requestReboot(String uniqueId) {
        return encodeCommand(uniqueId, "REBOOT#");
    }
    
    public ByteBuf setTimezone(String uniqueId, int timezone) {
        return encodeCommand(uniqueId, "GMT," + timezone + "#");
    }
    
    public ByteBuf createLoginMessage(String imei, short modelIdentifier, short timeZoneLanguage) {
        return new LoginMessage(imei, modelIdentifier, timeZoneLanguage).encode();
    }
    
    public ByteBuf createLocationMessage(String imei, double latitude, double longitude, double speed, double course) {
        return new LocationMessage(latitude, longitude, speed, course).encode();
    }
    
    public ByteBuf createHeartbeatMessage(String terminalInfo, int voltage, int gsm) {
        return new HeartbeatMessage(terminalInfo, voltage, gsm).encode();
    }
}
