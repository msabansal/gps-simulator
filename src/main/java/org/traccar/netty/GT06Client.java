package org.traccar.netty;

import org.traccar.csv.CsvParser;
import org.traccar.protocol.gt06.GT06FrameDecoder;
import org.traccar.protocol.gt06.GT06ProtocolEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import lombok.extern.slf4j.Slf4j;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.List;
import java.util.ArrayList;

@Slf4j
public class GT06Client {

    private String host;
    private int port;
    private String imei;
    private EventLoopGroup group;
    private Channel channel;
    private GT06ProtocolEncoder encoder;
    
    public GT06Client(String host, int port, String imei) {
        this.host = host;
        this.port = port;
        this.imei = imei;
        this.group = new NioEventLoopGroup();
        this.encoder = new GT06ProtocolEncoder();
    }

    public void start() throws Exception {
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new GT06FrameDecoder());
                            ch.pipeline().addLast(new ByteArrayEncoder());
                        }
                    });

            // Connect to the server
            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
            log.info("Connected to server {}:{}", host, port);

            // Perform login
            sendLoginMessage();
            
            // Try to load GPS points from CSV file first, then fallback to KML
            List<CsvParser.GpsPoint> gpsPoints = new ArrayList<>();
            
            // Try CSV file first
            String csvFilePath = "src/main/resources/positions.csv";
            CsvParser csvParser = new CsvParser();
            gpsPoints = csvParser.parseCsvFile(csvFilePath);
            
            if (!gpsPoints.isEmpty()) {
                // Send each position with some delay
                for (CsvParser.GpsPoint point : gpsPoints) {
                    sendLocationUpdate(point.getLatitude(), point.getLongitude(), 
                                        point.getSpeed(), point.getCourse());

                    // Wait a bit between position updates
                    Thread.sleep(100);
                }
            } else {
                // If no points from either source, use the default values
                double latitude = 23.66041388888889;
                double longitude = 74.00983388888889;
                double speed = 8.1;  // km/h
                double course = 27.00;  // degrees

                sendLocationUpdate(latitude, longitude, speed, course);
            }
            // Wait for the connection to close
            channel.close().sync();
            log.info("Connection closed");
        } finally {
            group.shutdownGracefully();
        }
    }

    public void sendLoginMessage() {
        if (channel != null && channel.isActive()) {
            ByteBuf buf = encoder.createLoginMessage(imei, (short) 0x8066, (short) 0x0001);
            channel.writeAndFlush(buf);
            log.info("Sent login message with IMEI: {}", imei);
        }
    }

    public void sendLocationUpdate(double latitude, double longitude, double speed, double course) {
        if (channel != null && channel.isActive()) {
            // Create a fixed date for consistent testing
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            
            // Create and send location message
            GT06ProtocolEncoder.LocationMessage.LocationMessageBuilder builder = 
                GT06ProtocolEncoder.LocationMessage.builder()
                    .latitude(latitude)
                    .longitude(longitude)
                    .speed(speed)
                    .course(course)
                    .time(calendar)
                    .mcc((short) 404) // India MCC
                    .mnc((byte) 20)  // Example MNC
                    .lac((short) 9767) // Example LAC
                    .cellId(23456) // Example Cell ID
                    .acc((byte) 1) // GPS accuracy (1 = valid)
                    .uploadMode((byte) 0) // Normal upload
                    .realTimeUpload((byte) 1) // Real-time upload
                    .mileage(0); // Zero mileage
            
            ByteBuf buf = builder.build().encode();
            channel.writeAndFlush(buf);
            log.info("Sent location update: lat={}, lon={}, speed={}, course={}", 
                    latitude, longitude, speed, course);
        }
    }
    
    // Main method or entry point for the application
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5023;
        String imei = args.length > 2 ? args[2] : "869925070485891";
        
        GT06Client client = new GT06Client(host, port, imei);
        try {
            client.start();
        } catch (Exception e) {
            log.error("Error running client: {}", e.getMessage(), e);
        }
    }
}
