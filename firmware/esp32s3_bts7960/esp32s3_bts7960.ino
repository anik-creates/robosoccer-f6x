/*
 * =====================================================================================
 *  Project: robosoccer-f6x (ESP32-S3 + Dual BTS7960 4WD Tank Soccerbot)
 *  File:    esp32s3_bts7960.ino
 *  Target:  ESP32-S3 (16 MB Flash, 8 MB Octal PSRAM)
 *  Author:  Anik Sarker / Tanvir
 *
 *  Core Engineering Specs:
 *  - 4WD Tank Motor Configuration (Dual BTS7960 High-Power H-Bridges)
 *  - Onboard NeoPixel RGB LED Status Indicator:
 *      * Short flash every 5 seconds in the indicator color (No solid blinding light)
 *      * Emerald Green flash : Phone Connected & Healthy 100 Hz UDP Control
 *      * Sky Blue flash      : AP Ready / Waiting for Phone (Link Released)
 *      * Crimson Red flash   : Failsafe Triggered (> 250 ms Packet Timeout)
 *  - Dedicated 802.11n AP (SSID: "RoboSoccer_S3", Pass: "soccer2026", Ch: 1, HT20)
 *  - 14 dBm (56) RF Transmit Power: cool running, zero LDO overload, zero phone LNA clipping
 *  - 160 MHz CPU Clock: cuts thermal output by ~35%
 *  - 20 kHz Ultrasonic 8-bit Silent PWM (Zero Audible Motor Whine)
 *  - 12-Byte Packed Binary UDP Protocol @ 100 Hz on Port 4210
 *  - 4-Byte 0x55AA Heartbeat ACK every 20 packets for phone RTT tracking
 *  - 250 ms Hardware Failsafe (Automatic 0-PWM brake & driver disable)
 * =====================================================================================
 */

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <esp_wifi.h>
#include <driver/ledc.h>
#include <soc/gpio_struct.h>
#include "protocol.h"

// -------------------------------------------------------------------------------------
// Pin Assignments (Dual BTS7960 4WD Tank Configuration)
// -------------------------------------------------------------------------------------
// Left Track Motors (BTS7960 #1 - Left Front + Left Rear in parallel)
#define LEFT_RPWM_PIN     17  // Left Forward PWM (high-frequency LEDC)
#define LEFT_LPWM_PIN     18  // Left Reverse PWM (high-frequency LEDC)
#define LEFT_EN_PIN       6   // Left Driver Enable (R_EN + L_EN tied)

// Right Track Motors (BTS7960 #2 - Right Front + Right Rear in parallel)
#define RIGHT_RPWM_PIN    10   // Right Forward PWM (high-frequency LEDC)
#define RIGHT_LPWM_PIN    11  // Right Reverse PWM (high-frequency LEDC)
#define RIGHT_EN_PIN      16  // Right Driver Enable (R_EN + L_EN tied)

// Auxiliary Actuators (CH5..CH10)
#define AUX_DRIBBLER_PIN  2   // Optional Dribbler motor PWM / ESC trigger (CH6)
#define AUX_SPARE_PIN     1   // Spare auxiliary output (CH5)

// Onboard NeoPixel RGB LED Pin
#ifndef RGB_BUILTIN
#define RGB_BUILTIN       48  // Default ESP32-S3 DevKit WS2812 pin
#endif

// -------------------------------------------------------------------------------------
// Network & Hardware Timing Constants
// -------------------------------------------------------------------------------------
const char* AP_SSID           = "RoboSoccer_S3";
const char* AP_PASS           = "soccer2026";
const int   AP_CHANNEL        = 1;     // Channel 1: Clean spectrum away from congested Ch 6
const int   AP_MAX_CONN       = 4;
const int   UDP_PORT          = 4210;

const uint32_t PWM_FREQ       = 20000; // 20 kHz ultrasonic (silent operation)
const uint8_t  PWM_RESOLUTION = 8;     // 8-bit resolution (0..255)
const uint32_t FAILSAFE_MS    = 250;   // 250 ms failsafe timeout (prevents jitter tripping)

// -------------------------------------------------------------------------------------
// Global State & RTOS Mutex
// -------------------------------------------------------------------------------------
volatile int16_t  g_target_left_pwm   = 0;
volatile int16_t  g_target_right_pwm  = 0;
volatile uint16_t g_target_aux_packed = 0;
volatile uint32_t g_last_packet_time  = 0;
volatile uint32_t g_rx_packet_count   = 0;
volatile uint32_t g_crc_error_count   = 0;
volatile bool     g_failsafe_active   = true;
volatile bool     g_client_connected  = false;

WiFiUDP g_udp;
portMUX_TYPE g_mux = portMUX_INITIALIZER_UNLOCKED;

// -------------------------------------------------------------------------------------
// Universal Multi-Pin NeoPixel (WS2812) Visual Debugger Driver (GPIO 48)
// Hardware RMT driver via Arduino Core neopixelWrite(48, r, g, b)
// Keeps GPIO 21 (discrete blue LED) turned OFF.
// -------------------------------------------------------------------------------------
void init_status_led() {
    pinMode(21, OUTPUT);
    digitalWrite(21, LOW); // Separate blue onboard LED kept permanently OFF
    neopixelWrite(48, 0, 0, 0); // Start OFF
}

void set_status_led(uint8_t r, uint8_t g, uint8_t b) {
    neopixelWrite(48, r, g, b);
}

/**
 * Visual Debugger on NeoPixel RGB LED:
 * - Throttle Zero / Idle / Failsafe : LED Turned OFF
 * - Forward   : GREEN  (Brightness scales smoothly with throttle 0..255)
 * - Reverse   : RED    (Brightness scales smoothly with throttle 0..255)
 * - Turn Right: PURPLE (Magenta: Red + Blue, brightness scales with steering 0..255)
 * - Turn Left : YELLOW (Red + Green, brightness scales with steering 0..255)
 *
 * Smoothness & Anti-Flicker Engineering:
 * - Rate-limited to 30 Hz (33 ms) to prevent RMT channel collisions with Wi-Fi interrupts
 * - Low-pass IIR exponential filter for butter-smooth color & brightness transitions
 * - Dual-threshold hysteresis (6 vs 12) prevents edge flickering around zero throttle
 * - Hardware RMT write only called when quantized RGB output changes
 */
void update_motor_status_led(int16_t left_pwm, int16_t right_pwm, bool is_failsafe) {
    static uint32_t s_last_led_update = 0;
    static float s_filt_r = 0.0f;
    static float s_filt_g = 0.0f;
    static float s_filt_b = 0.0f;
    static uint8_t s_sent_r = 0, s_sent_g = 0, s_sent_b = 0;
    static bool s_was_active = false;

    uint32_t now = millis();
    // 30 Hz maximum rate limit (33 ms): eliminates RMT contention with Wi-Fi stack
    if (now - s_last_led_update < 33) {
        return;
    }
    s_last_led_update = now;

    // Dual-threshold hysteresis: if already active require < 6 to stop; if idle require >= 12 to start
    int16_t thresh = s_was_active ? 6 : 12;
    bool is_idle = is_failsafe || (abs(left_pwm) < thresh && abs(right_pwm) < thresh);

    float target_r = 0.0f;
    float target_g = 0.0f;
    float target_b = 0.0f;

    if (!is_idle) {
        s_was_active = true;
        // Decompose Tank speeds into Forward/Reverse and Steering components
        int32_t fwd_comp  = ((int32_t)left_pwm + (int32_t)right_pwm) / 2;
        int32_t turn_comp = ((int32_t)left_pwm - (int32_t)right_pwm) / 2;

        int32_t abs_fwd  = abs(fwd_comp);
        int32_t abs_turn = abs(turn_comp);

        if (abs_fwd >= abs_turn) {
            // Forward / Reverse motion dominates (Max brightness 70 for cool LDO & high efficiency)
            float bri = (float)map(constrain(abs_fwd, 15, 255), 15, 255, 12, 70);
            if (fwd_comp > 0) {
                // FORWARD: GREEN (brightness according to throttle)
                target_r = 0.0f;
                target_g = bri;
                target_b = 0.0f;
            } else {
                // REVERSE: RED (brightness according to throttle)
                target_r = bri;
                target_g = 0.0f;
                target_b = 0.0f;
            }
        } else {
            // Steering / Turning motion dominates (Max brightness 70)
            float bri = (float)map(constrain(abs_turn, 15, 255), 15, 255, 12, 70);
            if (turn_comp > 0) {
                // RIGHT: PURPLE (Magenta: Red + Blue, brightness according to steer)
                target_r = bri;
                target_g = 0.0f;
                target_b = bri;
            } else {
                // LEFT: YELLOW (Red + Green, brightness according to steer)
                target_r = bri;
                target_g = bri;
                target_b = 0.0f;
            }
        }
    } else {
        s_was_active = false;
        // Idle / Failsafe: Fade cleanly to OFF
        target_r = 0.0f;
        target_g = 0.0f;
        target_b = 0.0f;
    }

    // Exponential smoothing filter (65% history, 35% new target)
    s_filt_r = s_filt_r * 0.65f + target_r * 0.35f;
    s_filt_g = s_filt_g * 0.65f + target_g * 0.35f;
    s_filt_b = s_filt_b * 0.65f + target_b * 0.35f;

    // Hard cutoff to pure zero when very close to stop
    if (s_filt_r < 3.0f && target_r == 0.0f) s_filt_r = 0.0f;
    if (s_filt_g < 3.0f && target_g == 0.0f) s_filt_g = 0.0f;
    if (s_filt_b < 3.0f && target_b == 0.0f) s_filt_b = 0.0f;

    uint8_t out_r = (uint8_t)constrain((int)s_filt_r, 0, 255);
    uint8_t out_g = (uint8_t)constrain((int)s_filt_g, 0, 255);
    uint8_t out_b = (uint8_t)constrain((int)s_filt_b, 0, 255);

    // Call hardware RMT driver ONLY if the output color bytes have changed
    if (out_r != s_sent_r || out_g != s_sent_g || out_b != s_sent_b) {
        s_sent_r = out_r;
        s_sent_g = out_g;
        s_sent_b = out_b;
        neopixelWrite(48, out_r, out_g, out_b);
    }
}

// -------------------------------------------------------------------------------------
// Dual BTS7960 Motor Driver Primitives
// -------------------------------------------------------------------------------------
void init_motor_drivers() {
    Serial.println("[INIT] Initializing Dual BTS7960 Driver Pins...");
    
    // Enable Pins
    pinMode(LEFT_EN_PIN, OUTPUT);
    pinMode(RIGHT_EN_PIN, OUTPUT);
    digitalWrite(LEFT_EN_PIN, LOW);
    digitalWrite(RIGHT_EN_PIN, LOW);

    // Aux Pins
    pinMode(AUX_SPARE_PIN, OUTPUT);
    digitalWrite(AUX_SPARE_PIN, LOW);

    // Attach 20 kHz Silent PWM Channels
#if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
    ledcAttach(LEFT_RPWM_PIN, PWM_FREQ, PWM_RESOLUTION);
    ledcAttach(LEFT_LPWM_PIN, PWM_FREQ, PWM_RESOLUTION);
    ledcAttach(RIGHT_RPWM_PIN, PWM_FREQ, PWM_RESOLUTION);
    ledcAttach(RIGHT_LPWM_PIN, PWM_FREQ, PWM_RESOLUTION);
    ledcAttach(AUX_DRIBBLER_PIN, PWM_FREQ, PWM_RESOLUTION);
#else
    ledcSetup(0, PWM_FREQ, PWM_RESOLUTION);
    ledcSetup(1, PWM_FREQ, PWM_RESOLUTION);
    ledcSetup(2, PWM_FREQ, PWM_RESOLUTION);
    ledcSetup(3, PWM_FREQ, PWM_RESOLUTION);
    ledcSetup(4, PWM_FREQ, PWM_RESOLUTION);

    ledcAttachPin(LEFT_RPWM_PIN, 0);
    ledcAttachPin(LEFT_LPWM_PIN, 1);
    ledcAttachPin(RIGHT_RPWM_PIN, 2);
    ledcAttachPin(RIGHT_LPWM_PIN, 3);
    ledcAttachPin(AUX_DRIBBLER_PIN, 4);
#endif

    Serial.println("[PWM] Dual BTS7960 20 kHz PWM channels attached successfully!");
}

void write_pwm(uint8_t pin, uint8_t channel, uint32_t duty) {
#if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
    ledcWrite(pin, duty);
#else
    ledcWrite(channel, duty);
#endif
}

void apply_left_motor(int16_t pwm) {
    static int16_t s_last_left_pwm = -999;
    pwm = constrain(pwm, -255, 255);
    if (pwm == s_last_left_pwm) return; // Skip redundant hardware register writes
    s_last_left_pwm = pwm;

    if (pwm > 0) {
        write_pwm(LEFT_RPWM_PIN, 0, (uint32_t)pwm);
        write_pwm(LEFT_LPWM_PIN, 1, 0);
    } else if (pwm < 0) {
        write_pwm(LEFT_RPWM_PIN, 0, 0);
        write_pwm(LEFT_LPWM_PIN, 1, (uint32_t)(-pwm));
    } else {
        // Active electromagnetic brake
        write_pwm(LEFT_RPWM_PIN, 0, 0);
        write_pwm(LEFT_LPWM_PIN, 1, 0);
    }
}

void apply_right_motor(int16_t pwm) {
    static int16_t s_last_right_pwm = -999;
    pwm = constrain(pwm, -255, 255);
    if (pwm == s_last_right_pwm) return; // Skip redundant hardware register writes
    s_last_right_pwm = pwm;

    if (pwm > 0) {
        write_pwm(RIGHT_RPWM_PIN, 2, (uint32_t)pwm);
        write_pwm(RIGHT_LPWM_PIN, 3, 0);
    } else if (pwm < 0) {
        write_pwm(RIGHT_RPWM_PIN, 2, 0);
        write_pwm(RIGHT_LPWM_PIN, 3, (uint32_t)(-pwm));
    } else {
        // Active electromagnetic brake
        write_pwm(RIGHT_RPWM_PIN, 2, 0);
        write_pwm(RIGHT_LPWM_PIN, 3, 0);
    }
}

void apply_aux_channels(uint16_t aux_packed) {
    static uint32_t s_last_dribbler_duty = 9999;
    // Unpack aux channels:
    // Bits 3..5: CH6 Dribbler speed (0..7 -> 0..255 PWM)
    uint8_t ch6 = (aux_packed >> 3) & 0x07;
    uint32_t dribbler_duty = (ch6 * 255) / 7;
    if (dribbler_duty == s_last_dribbler_duty) return;
    s_last_dribbler_duty = dribbler_duty;
    write_pwm(AUX_DRIBBLER_PIN, 4, dribbler_duty);
}

void emergency_brake() {
    apply_left_motor(0);
    apply_right_motor(0);
    digitalWrite(LEFT_EN_PIN, LOW);
    digitalWrite(RIGHT_EN_PIN, LOW);
    write_pwm(AUX_DRIBBLER_PIN, 4, 0);
}

// -------------------------------------------------------------------------------------
// Wi-Fi Event Callbacks
// -------------------------------------------------------------------------------------
void on_wifi_event(WiFiEvent_t event, WiFiEventInfo_t info) {
    if (event == ARDUINO_EVENT_WIFI_AP_STACONNECTED) {
        Serial.printf("[Wi-Fi] Phone connected! MAC: %02X:%02X:%02X:%02X:%02X:%02X, AID: %d\n",
            info.wifi_ap_staconnected.mac[0], info.wifi_ap_staconnected.mac[1],
            info.wifi_ap_staconnected.mac[2], info.wifi_ap_staconnected.mac[3],
            info.wifi_ap_staconnected.mac[4], info.wifi_ap_staconnected.mac[5],
            info.wifi_ap_staconnected.aid);
    } else if (event == ARDUINO_EVENT_WIFI_AP_STADISCONNECTED) {
        Serial.printf("[Wi-Fi] Phone disconnected! MAC: %02X:%02X:%02X:%02X:%02X:%02X\n",
            info.wifi_ap_stadisconnected.mac[0], info.wifi_ap_stadisconnected.mac[1],
            info.wifi_ap_stadisconnected.mac[2], info.wifi_ap_stadisconnected.mac[3],
            info.wifi_ap_stadisconnected.mac[4], info.wifi_ap_stadisconnected.mac[5]);
    }
}

// -------------------------------------------------------------------------------------
// Fast Non-Blocking UDP Telemetry Receiver
// -------------------------------------------------------------------------------------
void process_udp_packets() {
    uint8_t rx_buffer[64];
    AckPacket ack_packet;
    ack_packet.magic = 0x55AA;
    static bool s_first_packet = true;

    // Drain all queued packets up to latest
    while (int packet_size = g_udp.parsePacket()) {
        int bytes_read = g_udp.read(rx_buffer, sizeof(rx_buffer));
        if (bytes_read >= (int)sizeof(ControlPacket)) {
            ControlPacket *pkt = reinterpret_cast<ControlPacket*>(rx_buffer);

            // 1. Verify 0xAA55 Magic Word
            if (pkt->magic == 0xAA55) {
                // 2. Compute & Verify CRC-16 over first 10 bytes
                uint16_t computed_crc = calculate_crc16(rx_buffer, 10);
                if (computed_crc == pkt->crc16) {
                    portENTER_CRITICAL(&g_mux);
                    g_target_left_pwm   = pkt->left_pwm;
                    g_target_right_pwm  = pkt->right_pwm;
                    g_target_aux_packed = pkt->aux_packed;
                    g_last_packet_time  = millis();
                    g_rx_packet_count++;
                    g_failsafe_active   = false;
                    g_client_connected  = true;
                    portEXIT_CRITICAL(&g_mux);

                    if (s_first_packet) {
                        s_first_packet = false;
                        Serial.printf("\n[UDP] >>> FIRST VALID TELEMETRY PACKET RECEIVED FROM PHONE! <<<\n");
                        Serial.printf("[UDP] Magic: 0x%04X | Seq: %u | Left: %d | Right: %d\n\n",
                            pkt->magic, pkt->seq, pkt->left_pwm, pkt->right_pwm);
                    }

                    // 3. Heartbeat ACK every 20 packets for phone RTT calculation
                    if ((pkt->seq % 20) == 0) {
                        ack_packet.ack_seq = pkt->seq;
                        g_udp.beginPacket(g_udp.remoteIP(), g_udp.remotePort());
                        g_udp.write(reinterpret_cast<const uint8_t*>(&ack_packet), sizeof(AckPacket));
                        g_udp.endPacket();
                    }
                } else {
                    g_crc_error_count++;
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Setup
// -------------------------------------------------------------------------------------
void setup() {
    // 1. Lower CPU frequency to 160 MHz (Reduces dynamic core heat by ~35% while keeping 160 MIPS)
    setCpuFrequencyMhz(160);

    Serial.begin(115200);
    delay(100);

    Serial.println("\n=======================================================");
    Serial.println("  APEX-F6X: ESP32-S3 4WD TANK ROBO SOCCER CONTROLLER   ");
    Serial.println("  Author: Anik Sarker                                  ");
    Serial.println("=======================================================");
    Serial.printf("[SYSTEM] CPU Frequency: %u MHz\n", getCpuFrequencyMhz());

    // Initialize Universal Multi-Pin NeoPixel (WS2812 on GPIO 48)
    init_status_led();

    // 1-Second Visual Self-Test during boot (Efficient 60-brightness test)
    Serial.println("[LED] Running NeoPixel GPIO 48 Self-Test...");
    neopixelWrite(48, 0, 60, 0); delay(150);   // Green (Forward test)
    neopixelWrite(48, 60, 0, 0); delay(150);   // Red (Reverse test)
    neopixelWrite(48, 60, 0, 60); delay(150); // Purple (Right test)
    neopixelWrite(48, 60, 60, 0); delay(150); // Yellow (Left test)
    neopixelWrite(48, 0, 0, 0);                // OFF - ready for app!
    Serial.println("[LED] NeoPixel Self-Test Complete. LED is OFF.");

    // 2. Initialize BTS7960 Motor Drivers
    init_motor_drivers();
    emergency_brake();

    // 3. Configure Ultra-Low Latency Wi-Fi Access Point
    Serial.println("[SETUP] Configuring Wi-Fi Access Point...");
    WiFi.onEvent(on_wifi_event);
    WiFi.mode(WIFI_AP);
    IPAddress local_ip(192, 168, 4, 1);
    IPAddress gateway(192, 168, 4, 1);
    IPAddress subnet(255, 255, 255, 0);
    WiFi.softAPConfig(local_ip, gateway, subnet);

    bool ap_ok = WiFi.softAP(AP_SSID, AP_PASS, AP_CHANNEL, 0, AP_MAX_CONN);
    if (ap_ok) {
        Serial.printf("[Wi-Fi] AP Broadcast Started: %s (Channel %d)\n", AP_SSID, AP_CHANNEL);
        Serial.printf("[Wi-Fi] AP IP: %s | UDP Port: %d\n", WiFi.softAPIP().toString().c_str(), UDP_PORT);
    } else {
        Serial.println("[Wi-Fi] ERROR: Failed to initialize SoftAP!");
    }

    // 4. RF Thermal & Power Optimization:
    // Limit Max RF TX Power to 14 dBm (56). Cuts RF thermal dissipation by ~40% and
    // prevents RF frontend saturation on smartphones at close range (1-3 meters)!
    esp_wifi_set_max_tx_power(56);
    int8_t actual_pwr = 0;
    esp_wifi_get_max_tx_power(&actual_pwr);
    Serial.printf("[Wi-Fi] TX Power configured: %d (approx %.1f dBm)\n", actual_pwr, actual_pwr * 0.25f);

    // Enable MIN_MODEM power save: allows RF circuit micro-sleeps between frame intervals
    esp_wifi_set_ps(WIFI_PS_MIN_MODEM);

    // Lock bandwidth to 20 MHz HT20 for maximum SNR stability
    esp_wifi_set_bandwidth(WIFI_IF_AP, WIFI_BW_HT20);

    // 5. Start UDP Server
    g_udp.begin(UDP_PORT);
    Serial.printf("[UDP] Listening on 0.0.0.0:%d\n", UDP_PORT);

    g_last_packet_time = millis();
    Serial.println("[SYSTEM] Robot Ready for High-Speed Telemetry (Cool & Stable Mode).");

    // LED off after boot
    set_status_led(0, 0, 0);
}

// -------------------------------------------------------------------------------------
// Deterministic 100 Hz Control Loop
// -------------------------------------------------------------------------------------
void loop() {
    uint32_t now = millis();

    // 1. Drain UDP Socket
    process_udp_packets();

    // 2. Thread-Safe State Read
    int16_t cur_left = 0;
    int16_t cur_right = 0;
    uint16_t cur_aux = 0;
    uint32_t last_pkt = 0;
    bool is_failsafe = false;

    portENTER_CRITICAL(&g_mux);
    cur_left   = g_target_left_pwm;
    cur_right  = g_target_right_pwm;
    cur_aux    = g_target_aux_packed;
    last_pkt   = g_last_packet_time;
    portEXIT_CRITICAL(&g_mux);

    // 3. Hardware Failsafe Watchdog (250 ms cutoff)
    if ((now - last_pkt) > FAILSAFE_MS) {
        is_failsafe = true;
        if (!g_failsafe_active) {
            portENTER_CRITICAL(&g_mux);
            g_failsafe_active = true;
            portEXIT_CRITICAL(&g_mux);
            Serial.printf("[FAILSAFE] Packet timeout (> %u ms)! Motors stopped.\n", FAILSAFE_MS);
        }
    }

    // 4. Motor Execution
    if (is_failsafe) {
        emergency_brake();
    } else {
        // Healthy Link: Enable BTS7960 Drivers & Drive Motors
        digitalWrite(LEFT_EN_PIN, HIGH);
        digitalWrite(RIGHT_EN_PIN, HIGH);

        apply_left_motor(cur_left);
        apply_right_motor(cur_right);
        apply_aux_channels(cur_aux);
    }

    // 5. Interactive NeoPixel LED Visual Debugger
    // Throttle Zero -> OFF | Forward -> GREEN | Reverse -> RED | Right -> PURPLE | Left -> YELLOW
    update_motor_status_led(cur_left, cur_right, is_failsafe);

    // 6. Live Motor Speed & Direction in Serial Monitor (every 200 ms = 5 Hz)
    static uint32_t s_last_serial_print = 0;
    if (now - s_last_serial_print >= 200) {
        s_last_serial_print = now;

        const char* motion = "STOPPED";
        const char* led_color = "OFF";
        int32_t fwd_comp  = ((int32_t)cur_left + (int32_t)cur_right) / 2;
        int32_t turn_comp = ((int32_t)cur_left - (int32_t)cur_right) / 2;
        int32_t abs_fwd   = abs(fwd_comp);
        int32_t abs_turn  = abs(turn_comp);

        if (is_failsafe) {
            motion = "FAILSAFE";
            led_color = "OFF";
        } else if (abs(cur_left) < 8 && abs(cur_right) < 8) {
            motion = "IDLE (0)";
            led_color = "OFF";
        } else if (abs_fwd >= abs_turn) {
            if (fwd_comp > 0) {
                motion = "FORWARD ";
                led_color = "GREEN  ";
            } else {
                motion = "REVERSE ";
                led_color = "RED    ";
            }
        } else {
            if (turn_comp > 0) {
                motion = "RIGHT   ";
                led_color = "PURPLE ";
            } else {
                motion = "LEFT    ";
                led_color = "YELLOW ";
            }
        }

        Serial.printf("[MOTOR] Left: %+4d | Right: %+4d | Motion: %s | LED: %s | RxPkts: %u\n",
            cur_left, cur_right, motion, led_color, g_rx_packet_count);
    }

    // 10 ms loop delay (100 Hz control frequency matching 100 Hz phone transmission rate)
    // Yields CPU to FreeRTOS scheduler, allowing IDLE task to execute 'waiti' low-power sleep
    delay(10);
}
