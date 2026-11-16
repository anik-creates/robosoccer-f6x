#pragma once
#include <stdint.h>
#include <stddef.h>

#pragma pack(push, 1)
// 12-Byte Packed Binary Control Protocol (Little-Endian)
struct ControlPacket {
    uint16_t magic;       // 0xAA55
    uint16_t seq;         // Rolling sequence counter (0..65535)
    int16_t  left_pwm;    // Left 4WD track signed PWM (-255..+255)
    int16_t  right_pwm;   // Right 4WD track signed PWM (-255..+255)
    uint16_t aux_packed;  // Packed auxiliary channels (CH5..CH10)
    uint16_t crc16;       // CRC-16 over bytes 0..9 (Poly 0xA001, Init 0xFFFF)
};

// 4-Byte Bidirectional Heartbeat Acknowledgment Packet
struct AckPacket {
    uint16_t magic;       // 0x55AA
    uint16_t ack_seq;     // Sequence number of acknowledged packet
};
#pragma pack(pop)

// Fast CRC-16 calculation (Poly 0xA001, Init 0xFFFF)
inline uint16_t calculate_crc16(const uint8_t *data, size_t len) {
    uint16_t crc = 0xFFFF;
    for (size_t i = 0; i < len; i++) {
        crc ^= (uint16_t)data[i];
        for (int b = 0; b < 8; b++) {
            if (crc & 0x0001) {
                crc = (crc >> 1) ^ 0xA001;
            } else {
                crc = crc >> 1;
            }
        }
    }
    return crc;
}
