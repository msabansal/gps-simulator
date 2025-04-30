package org.traccar.protocol.gt06;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GT06ProtocolEncoderTest {

    private GT06ProtocolEncoder encoder;

    @BeforeEach
    public void setUp() {
        encoder = new GT06ProtocolEncoder();
    }

    @Test
    public void testLoginMessage() {
        String imei = "869925070485899";
        ByteBuf buf = encoder.createLoginMessage(imei, (short) 0x8066, (short) 0x0001);
        String message = ByteBufUtil.hexDump(buf);
        System.out.println("Login Message: " + message);
        assertNotNull(buf, "Login message should not be null");
    }

    @Test
    public void testLocationMessage() {
        double latitude = 23.66041388888889;
        double longitude = 74.00983388888889;
        double speed = 8.1;  // km/h
        double course = 27.00;  // degrees


        // Create a fixed date for consistent testing
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(2025, Calendar.APRIL, 29, 0xc, 0x3, 4);

        // Create location message with the fixed time
        GT06ProtocolEncoder.LocationMessage locationMessage = 
            GT06ProtocolEncoder.LocationMessage.builder()
                .latitude(latitude)
                .longitude(longitude)
                .speed(speed)
                .course(course)
                .time(calendar)
                .mcc((short) 0x0194)
                    .mnc((byte) 0x46)
                    .lac((short) 0x01f2)
                    .cellId(0x0020a0)
                    .acc((byte) 0x01)
                    .uploadMode((byte) 0x00)
                    .realTimeUpload((byte) 0x00)
                    .mileage(0x025a3647)
                .build();

        ByteBuf buf = locationMessage.encode();
        
        // Check that buffer is not null
        assertNotNull(buf, "Location message should not be null");
        
        // Dump hex for debugging
        String message = ByteBufUtil.hexDump(buf);
        System.out.println("Location Message: " + message);
        
        // Verify buffer contains expected data
        // The following assertions could be added once the expected format is verified:
        // assertEquals(expected byte count, buf.readableBytes(), "Message length should match expected value");
        // Specific byte position checks can be added based on the protocol specification
    }
}
