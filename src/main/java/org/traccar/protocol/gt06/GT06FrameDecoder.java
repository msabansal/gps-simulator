package org.traccar.protocol.gt06;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.util.List;

@Slf4j
public class GT06FrameDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 5) { // Minimum frame length (header + length + trailer)
            return;
        }

        in.markReaderIndex();
        log.info("Message: {}", ByteBufUtil.hexDump(in));
        // Check header
        int header = in.readShort();
        if (header != 0x7878 && header != 0x7979) {
            in.resetReaderIndex();
            return;
        }

        // Get length
        int length;
        int lengthFieldSize;
        if (header == 0x7878) {
            length = in.readUnsignedByte();
            lengthFieldSize = 1;
        } else {
            if (in.readableBytes() < 2) {
                in.resetReaderIndex();
                return;
            }
            length = in.readUnsignedShort();
            lengthFieldSize = 2;
        }

        // Check if the full frame is available
        int payloadLength = length;
        int totalLength = payloadLength + 2; // Payload and trailer

        if (in.readableBytes() < totalLength) {
            in.resetReaderIndex();
            return;
        }

        // Extract the frame
        ByteBuf frame = in.readRetainedSlice(payloadLength - 4);

        int index = in.readUnsignedShort();
        int checkSum = in.readUnsignedShort();
        ByteBuffer subFrame = in.nioBuffer(2, payloadLength - 2 + lengthFieldSize);
        int calculatedChecksum = Checksum.crc16(Checksum.CRC16_X25, subFrame);
        if (checkSum != calculatedChecksum) {
            log.warn("Invalid checksum: expected 0x{}, got 0x{}", Integer.toHexString(checkSum), Integer.toHexString(calculatedChecksum));
            frame.release();
            ctx.close();
            return;
        };

        // Check for delimiter
        if (in.readableBytes() >= 2) {
            int delimiter = in.readShort();
            if (delimiter != 0x0D0A) {
                log.warn("Invalid frame delimiter: 0x{}", Integer.toHexString(delimiter));
            }
        }

        out.add(frame);
    }
}
