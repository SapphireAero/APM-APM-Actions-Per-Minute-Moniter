package com.apm.monitor.core.hook;

import com.sun.jna.Pointer;

/** 只提取操作次数，不保存按键内容、鼠标位置或设备标识。 */
final class RawInputDecoder {
    private RawInputDecoder() { }

    static int countActions(Pointer packet, int bytes, int headerSize) {
        if (packet == null || headerSize < 16 || bytes < headerSize) {
            return 0;
        }
        int packetSize = packet.getInt(4);
        if (packetSize < headerSize || packetSize > bytes) {
            return 0;
        }
        int type = packet.getInt(0);
        if (type == 1 && packetSize >= headerSize + 16) {
            int makeCode = Short.toUnsignedInt(packet.getShort(headerSize));
            int flags = Short.toUnsignedInt(packet.getShort(headerSize + 2));
            int virtualKey = Short.toUnsignedInt(packet.getShort(headerSize + 6));
            return (flags & 1) == 0 && makeCode != 0xff && virtualKey > 0 && virtualKey < 0xff ? 1 : 0;
        }
        if (type == 0 && packetSize >= headerSize + 24) {
            int buttonFlags = Short.toUnsignedInt(packet.getShort(headerSize + 4));
            // 五个按钮的 down 标志；release、移动和滚轮都不计数。
            return Integer.bitCount(buttonFlags & 0x0155);
        }
        return 0;
    }
}
