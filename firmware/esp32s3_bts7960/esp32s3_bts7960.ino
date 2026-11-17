/*
 * =====================================================================================
 *  Project: robosoccer-f6x (ESP32-S3 + Dual BTS7960 4WD Tank Soccerbot)
 *  File:    main.cpp
 *  Target:  ESP32-S3 (16 MB Flash, 8 MB Octal PSRAM)
 *  Author:  Anik Sarker / Tanvir
 *
 *  Core Engineering Specs:
 *  - 4WD Tank Motor Configuration (Dual BTS7960 High-Power H-Bridges)
 *  - Onboard NeoPixel RGB LED Status Indicator:
 *      * Short flash every 5 seconds in the indicator color (No solid blinding light)
 *      * Emerald Green flash : Phone Connected & Healthy 100 Hz UDP Control
 *      * Sky Blue flash      : AP Ready / Waiting for Phone (Link Released)
 *      * Crimson Red flash   : Failsafe Triggered (> 150 ms Packet Timeout)
 *  - Dedicated 802.11n AP (SSID: "RoboSoccer_S3", Pass: "soccer2026", Ch: 6, HT20)
 *  - esp_wifi_set_ps(WIFI_PS_NONE) for 0 ms modem-sleep latency spikes
 *  - 20 kHz Ultrasonic 8-bit Silent PWM (Zero Audible Motor Whine)
 *  - 12-Byte Packed Binary UDP Protocol @ 100 Hz on Port 4210
 *  - 4-Byte 0x55AA Heartbeat ACK every 20 packets for phone RTT tracking
 *  - 150 ms Hardware Failsafe (Automatic 0-PWM brake & driver disable)
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
#define RIGHT_RPWM_PIN    7   // Right Forward PWM (high-frequency LEDC)
#define RIGHT_LPWM_PIN    15  // Right Reverse PWM (high-frequency LEDC)
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
const int   AP_CHANNEL        = 6;
const int   AP_MAX_CONN       = 4;
const int   UDP_PORT          = 4210;

const uint32_t PWM_FREQ       = 20000; // 20 kHz ultrasonic (silent operation)
const uint8_t  PWM_RESOLUTION = 8;     // 8-bit resolution (0..255)
const uint32_t FAILSAFE_MS    = 150;   // 150 ms link timeout failsafe

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
// Universal Multi-Pin NeoPixel (WS2812) Status Driver
// Simultaneously drives GPIO 48, GPIO 38, and GPIO 21 at register level
// Works on DevKitC-1, YD-ESP32-S3, NodeMCU ESP32-S3, and generic clones.
// -------------------------------------------------------------------------------------
void init_status_led() {
    pinMode(48, OUTPUT);
    pinMode(38, OUTPUT);
    pinMode(21, OUTPUT);
    digitalWrite(48, LOW);
    digitalWrite(38, LOW);
    digitalWrite(21, LOW);
}

void set_status_led(uint8_t r, uint8_t g, uint8_t b) {
    // Upper pins in GPIO.out1: GPIO 48 (bit 16) and GPIO 38 (bit 6)
    const uint32_t mask1 = (1UL << (48 - 32)) | (1UL << (38 - 32));
    // Lower pin in GPIO.out: GPIO 21 (bit 21)
    const uint32_t mask0 = (1UL << 21);

    // Color order: GRB (24-bit)
    uint32_t color = ((uint32_t)g << 16) | ((uint32_t)r << 8) | (uint32_t)b;

    portDISABLE_INTERRUPTS();

    for (int i = 23; i >= 0; i--) {
        uint32_t bit = (color >> i) & 1;
        // 240 MHz CPU: 1 cycle = 4.167 ns
        // Bit 1: 800 ns High (~192 cycles), 450 ns Low (~108 cycles)
        // Bit 0: 350 ns High (~84 cycles), 900 ns Low (~216 cycles)
        uint32_t t_high = bit ? 192 : 84;
        uint32_t t_period = 300; // 1.25 us

        uint32_t start = __builtin_esp32_get_cycle_count();
        GPIO.out1_w1ts.val = mask1;
        GPIO.out_w1ts.val  = mask0;

        while ((__builtin_esp32_get_cycle_count() - start) < t_high) {}

        GPIO.out1_w1tc.val = mask1;
        GPIO.out_w1tc.val  = mask0;

        while ((__builtin_esp32_get_cycle_count() - start) < t_period) {}
    }

    portENABLE_INTERRUPTS();
    delayMicroseconds(80); // Latch
}

/**
 * Status LED Indicator:
 * Blinks for 150 ms in the current status color every 5 seconds (5000 ms).
 * Stays completely OFF for the other 4850 ms of the cycle (no blinding solid light).
 */
void update_status_led(uint32_t now, bool is_failsafe, bool client_connected) {
    const uint32_t BLINK_PERIOD_MS   = 5000;
    const uint32_t BLINK_DURATION_MS = 150;

    static bool s_was_in_blink = false;
    bool is_blink_window = (now % BLINK_PERIOD_MS) < BLINK_DURATION_MS;

    if (is_blink_window) {
        if (!s_was_in_blink) {
            s_was_in_blink = true;
            if (!is_failsafe && client_connected) {
                // Emerald Green: Connected & Active 100 Hz Control
                set_status_led(0, 255, 30);
            } else if (!client_connected) {
                // Sky Blue: AP Ready / Waiting for Phone (Link Released)
                set_status_led(0, 50, 255);
            } else {
                // Crimson Red: Failsafe Triggered (> 150 ms packet timeout)
                set_status_led(255, 0, 0);
            }
        }
    } else {
        if (s_was_in_blink) {
            s_was_in_blink = false;
            set_status_led(0, 0, 0); // Turn completely OFF
        }
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
    pwm = constrain(pwm, -255, 255);
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
    pwm = constrain(pwm, -255, 255);
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
    // Unpack aux channels:
    // Bits 3..5: CH6 Dribbler speed (0..7 -> 0..255 PWM)
    uint8_t ch6 = (aux_packed >> 3) & 0x07;
    uint32_t dribbler_duty = (ch6 * 255) / 7;
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
        if (packet_size >= (int)sizeof(ControlPacket)) {
            int bytes_read = g_udp.read(rx_buffer, sizeof(ControlPacket));
            if (bytes_read == sizeof(ControlPacket)) {
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
                            Serial.printf("[UDP] FIRST VALID PACKET RECEIVED! Magic: 0x%04X, Seq: %u, Left: %d, Right: %d\n",
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
        } else {
            g_udp.flush();
        }
    }
}

// -------------------------------------------------------------------------------------
// Setup
// -------------------------------------------------------------------------------------
void setup() {
    Serial.begin(115200);
    delay(200);

    Serial.println("\n=======================================================");
    Serial.println("  APEX-F6X: ESP32-S3 4WD TANK ROBO SOCCER CONTROLLER   ");
    Serial.println("  Author: Anik Sarker                                  ");
    Serial.println("=======================================================");

    // Initialize Universal Multi-Pin NeoPixel (WS2812)
    init_status_led();

    // Brief Yellow flash during boot (200 ms)
    set_status_led(180, 80, 0);

    // 1. Initialize BTS7960 Motor Drivers
    init_motor_drivers();
    emergency_brake();

    // 2. Configure Ultra-Low Latency Wi-Fi Access Point
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

    // 3. Disable modem-sleep power saving for zero packet jitter
    esp_err_t ps_err = esp_wifi_set_ps(WIFI_PS_NONE);
    if (ps_err == ESP_OK) {
        Serial.println("[Wi-Fi] Power Save Disabled: WIFI_PS_NONE (Ultra-Low Latency Mode)");
    }

    // Lock bandwidth to 20 MHz HT20 for maximum SNR stability
    esp_wifi_set_bandwidth(WIFI_IF_AP, WIFI_BW_HT20);

    // 4. Start UDP Server
    g_udp.begin(UDP_PORT);
    Serial.printf("[UDP] Listening on 0.0.0.0:%d\n", UDP_PORT);

    g_last_packet_time = millis();
    Serial.println("[SYSTEM] Robot Ready for High-Speed Telemetry.");

    // LED off after boot
    set_status_led(0, 0, 0);
}

// -------------------------------------------------------------------------------------
// Deterministic 500 Hz Control Loop
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

    // 3. Hardware Failsafe Watchdog (150 ms cutoff)
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

    // 5. Short Blink in Indicator Color Every 5 Seconds (No Solid Light)
    bool has_client = (WiFi.softAPgetStationNum() > 0) || g_client_connected;
    update_status_led(now, is_failsafe, has_client);

    // 6. Periodic Serial Diagnostics every 5 seconds
    static uint32_t s_last_stat_time = 0;
    if (now - s_last_stat_time >= 5000) {
        s_last_stat_time = now;
        Serial.printf("[STATS] Stations: %d | RX Pkts: %u | CRC Errs: %u | Failsafe: %d | PWM: L=%d R=%d\n",
            WiFi.softAPgetStationNum(), g_rx_packet_count, g_crc_error_count, is_failsafe, cur_left, cur_right);
    }

    // 2 ms loop delay (500 Hz control frequency)
    delay(2);
}
