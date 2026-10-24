package com.apm.monitor.core.hook;

import com.sun.jna.Memory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RawInputDecoderTest {
    @ParameterizedTest
    @ValueSource(ints = {16, 24})
    void keyboardCountsDownAndExtendedKeysButNotReleaseOrInvalidKeys(int header) {
        try (Memory packet = packet(1, header, 16)) {
            packet.setShort(header, (short) 0x1e);
            packet.setShort(header + 6, (short) 0x41);
            assertEquals(1, decode(packet, header));
            packet.setShort(header + 2, (short) 2);
            assertEquals(1, decode(packet, header));
            packet.setShort(header + 2, (short) 3);
            assertEquals(0, decode(packet, header));
            packet.setShort(header + 2, (short) 0);
            packet.setShort(header, (short) 0xff);
            assertEquals(0, decode(packet, header));
            packet.setShort(header, (short) 0x1e);
            packet.setShort(header + 6, (short) 0xff);
            assertEquals(0, decode(packet, header));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 24})
    void mouseCountsEachPressedButtonButNotMovementReleaseOrWheel(int header) {
        try (Memory packet = packet(0, header, 24)) {
            packet.setInt(header + 12, 100);
            assertEquals(0, decode(packet, header));
            packet.setShort(header + 4, (short) 0x155);
            assertEquals(5, decode(packet, header));
            packet.setShort(header + 4, (short) 0xaaa);
            assertEquals(0, decode(packet, header));
            packet.setShort(header + 4, (short) 0x405);
            assertEquals(2, decode(packet, header));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 24})
    void malformedOrUnsupportedPacketsAreIgnored(int header) {
        try (Memory packet = packet(1, header, 16)) {
            assertEquals(0, RawInputDecoder.countActions(packet, header - 1, header));
            packet.setInt(4, (int) packet.size() + 100);
            assertEquals(0, decode(packet, header));
            packet.setInt(4, header);
            assertEquals(0, decode(packet, header));
            packet.setInt(4, (int) packet.size());
            packet.setInt(0, 2);
            assertEquals(0, decode(packet, header));
        }
    }

    private static Memory packet(int type, int header, int body) {
        Memory packet = new Memory(header + body);
        packet.clear();
        packet.setInt(0, type);
        packet.setInt(4, (int) packet.size());
        return packet;
    }

    private static int decode(Memory packet, int header) {
        return RawInputDecoder.countActions(packet, (int) packet.size(), header);
    }
}
