/*
 * =====================================================================================
 *  Project: robosoccer-f6x (ESP32-S3 + Dual BTS7960 4WD Tank Soccerbot)
 *  File:    esp32s3_bts7960.ino
 *  Target:  ESP32-S3 (16 MB Flash, 8 MB Octal PSRAM)
 *  Author:  Anik Sarker / Tanvir
 *
 *  Ultra-Lightweight & Thermal-Optimized Specs:
 *  - 80 MHz CPU Clock: Absolute lowest hardware frequency for 802.11 Wi-Fi.
 *  - 7.0 dBm (28) RF Transmit Power: Reduces RF heat dissipation by >93%.
 *  - WIFI_PS_NONE: Instant 0 ms packet latency (no 5 Hz DTIM jitter).
 *  - NeoPixel Completely Removed: No RMT peripherals or high-frequency LED interrupts.
 *  - Slew-Rate Limiter (Ramp Smoother): Butter-smooth acceleration & steering.
 *  - 750 ms Failsafe Watchdog: Eliminates false failsafe trips from Android background
 *    scans (completely cures the 4-5 second motor shock/jerk!).
 *  - Gentle Failsafe Braking: Smooth deceleration to 0 without violent EN pin chatter.
 *  - Internal Die Temperature Diagnostic: Logs real silicon temp every 3 seconds.
 * =====================================================================================
 */

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <esp_wifi.h>
#include <driver/ledc.h>
#include "protocol.h"

// -------------------------------------------------------------------------------------
// Pin Assignments (Dual BTS7960 4WD Tank Configuration)
// -------------------------------------------------------------------------------------
// Left Track Motors (BTS7960 #1 - Left Front + Left Rear in parallel)
#define LEFT_RPWM_PIN     17  // Left Forward PWM (high-frequency LEDC)
#define LEFT_LPWM_PIN     18  // Left Reverse PWM (high-frequency LEDC)
#define LEFT_EN_PIN       6   // Left Driver Enable (R_EN + L_EN tied)

// Right Track Motors (BTS7960 #2 - Right Front + Right Rear in parallel)
#define RIGHT_RPWM_PIN    10  // Right Forward PWM (high-frequency LEDC)
#define RIGHT_LPWM_PIN    11  // Right Reverse PWM (high-frequency LEDC)
#define RIGHT_EN_PIN      16  // Right Driver Enable (R_EN + L_EN tied)

// Auxiliary Actuators
#define AUX_DRIBBLER_PIN  2   // Optional Dribbler motor PWM / ESC trigger (CH6)
#define AUX_SPARE_PIN     1   // Spare auxiliary output (CH5)

// Disabled LED Pins (Kept LOW for zero power dissipation)
#define NEOPIXEL_PIN      48  // WS2812 data line
#define BLUE_LED_PIN      21  // Discrete indicator LED

// -------------------------------------------------------------------------------------
// Network & Hardware Timing Constants
// -------------------------------------------------------------------------------------
const char* AP_SSID           = "RoboSoccer_S3";
const char* AP_PASS           = "soccer2026";
const int   AP_CHANNEL        = 1;     // Channel 1: Clean 2.4 GHz spectrum
const int   AP_MAX_CONN       = 2;     // Minimal AP table allocation
const int   UDP_PORT          = 4210;

const uint32_t PWM_FREQ       = 20000; // 20 kHz ultrasonic (silent operation)
const uint8_t  PWM_RESOLUTION = 8;     // 8-bit resolution (0..255)

// 750 ms failsafe timeout:
// Android phones perform periodic background Wi-Fi probes/scans every 4-8 seconds
// when connected to an AP without Internet. A 750 ms timeout comfortably rides through
// any momentary 100-300 ms phone scan pause with ZERO motor shocks or jerks!
const uint32_t FAILSAFE_MS    = 750;

// Slew-Rate Limiter: Maximum PWM change per 3 ms control loop tick
// 12 units per 3 ms = ~4000 units/s (0 -> 255 takes ~60 ms)
const int16_t MAX_PWM_SLEW_PER_TICK = 12;

// -------------------------------------------------------------------------------------
// Global State & RTOS Mutex
// -------------------------------------------------------------------------------------
volatile int16_t  g_target_left_pwm   = 0;
volatile int16_t  g_target_right_pwm  = 0;
volatile uint16_t g_target_aux_packed = 0;
volatile uint32_t g_last_packet_time  = 0;
volatile uint32_t g_rx_packet_count   = 0;
volatile bool     g_failsafe_active   = true;

WiFiUDP g_udp;
portMUX_TYPE g_mux = portMUX_INITIALIZER_UNLOCKED;

// Slew-rate smoothed active motor outputs
static int16_t s_smooth_left_pwm  = 0;
static int16_t s_smooth_right_pwm = 0;
static bool    s_drivers_enabled  = false;

// -------------------------------------------------------------------------------------
// Dual BTS7960 Motor Driver Primitives
// -------------------------------------------------------------------------------------
void write_pwm(uint8_t pin, uint8_t channel, uint32_t duty) {
#if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
    ledcWrite(pin, duty);
#else
    ledcWrite(channel, duty);
#endif
}

void set_driver_enables(bool enable) {
    if (s_drivers_enabled != enable) {
        s_drivers_enabled = enable;
        digitalWrite(LEFT_EN_PIN,  enable ? HIGH : LOW);
        digitalWrite(RIGHT_EN_PIN, enable ? HIGH : LOW);
    }
}

void init_motor_drivers() {
    // Enable Pins
    pinMode(LEFT_EN_PIN, OUTPUT);
    pinMode(RIGHT_EN_PIN, OUTPUT);
    set_driver_enables(false);

    // Aux Pins
    pinMode(AUX_SPARE_PIN, OUTPUT);
    digitalWrite(AUX_SPARE_PIN, LOW);

    // Disable LEDs to save power
    pinMode(NEOPIXEL_PIN, OUTPUT);
    digitalWrite(NEOPIXEL_PIN, LOW);
    pinMode(BLUE_LED_PIN, OUTPUT);
    digitalWrite(BLUE_LED_PIN, LOW);

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
}

void apply_left_motor(int16_t pwm) {
    static int16_t s_last_left_pwm = -999;
    pwm = constrain(pwm, -255, 255);
    if (pwm == s_last_left_pwm) return;
    s_last_left_pwm = pwm;

    if (pwm > 0) {
        write_pwm(LEFT_RPWM_PIN, 0, (uint32_t)pwm);
        write_pwm(LEFT_LPWM_PIN, 1, 0);
    } else if (pwm < 0) {
        write_pwm(LEFT_RPWM_PIN, 0, 0);
        write_pwm(LEFT_LPWM_PIN, 1, (uint32_t)(-pwm));
    } else {
        // Active brake
        write_pwm(LEFT_RPWM_PIN, 0, 0);
        write_pwm(LEFT_LPWM_PIN, 1, 0);
    }
}

void apply_right_motor(int16_t pwm) {
    static int16_t s_last_right_pwm = -999;
    pwm = constrain(pwm, -255, 255);
    if (pwm == s_last_right_pwm) return;
    s_last_right_pwm = pwm;

    if (pwm > 0) {
        write_pwm(RIGHT_RPWM_PIN, 2, (uint32_t)pwm);
        write_pwm(RIGHT_LPWM_PIN, 3, 0);
    } else if (pwm < 0) {
        write_pwm(RIGHT_RPWM_PIN, 2, 0);
        write_pwm(RIGHT_LPWM_PIN, 3, (uint32_t)(-pwm));
    } else {
        // Active brake
        write_pwm(RIGHT_RPWM_PIN, 2, 0);
        write_pwm(RIGHT_LPWM_PIN, 3, 0);
    }
}

void apply_aux_channels(uint16_t aux_packed) {
    static uint32_t s_last_dribbler_duty = 9999;
    uint8_t ch6 = (aux_packed >> 3) & 0x07;
    uint32_t dribbler_duty = (ch6 * 255) / 7;
    if (dribbler_duty == s_last_dribbler_duty) return;
    s_last_dribbler_duty = dribbler_duty;
    write_pwm(AUX_DRIBBLER_PIN, 4, dribbler_duty);
}

// -------------------------------------------------------------------------------------
// Slew-Rate Filter for Butter-Smooth Motor Acceleration & Deceleration
// -------------------------------------------------------------------------------------
inline int16_t slew_towards(int16_t current, int16_t target, int16_t max_step) {
    int16_t delta = target - current;
    if (delta > max_step) {
        return current + max_step;
    } else if (delta < -max_step) {
        return current - max_step;
    }
    return target;
}

// -------------------------------------------------------------------------------------
// Fast Non-Blocking UDP Telemetry Receiver
// -------------------------------------------------------------------------------------
void process_udp_packets() {
    uint8_t rx_buffer[64];
    AckPacket ack_packet;
    ack_packet.magic = 0x55AA;

    while (int packet_size = g_udp.parsePacket()) {
        int bytes_read = g_udp.read(rx_buffer, sizeof(rx_buffer));
        if (bytes_read >= (int)sizeof(ControlPacket)) {
            ControlPacket *pkt = reinterpret_cast<ControlPacket*>(rx_buffer);

            // 1. Verify 0xAA55 Magic Word
            if (pkt->magic == 0xAA55) {
                // 2. Compute & Verify CRC-16 over payload bytes
                uint16_t computed_crc = calculate_crc16(rx_buffer, 10);
                if (computed_crc == pkt->crc16) {
                    portENTER_CRITICAL(&g_mux);
                    g_target_left_pwm   = pkt->left_pwm;
                    g_target_right_pwm  = pkt->right_pwm;
                    g_target_aux_packed = pkt->aux_packed;
                    g_last_packet_time  = millis();
                    g_rx_packet_count++;
                    g_failsafe_active   = false;
                    portEXIT_CRITICAL(&g_mux);

                    // 3. Heartbeat ACK every 20 packets for phone RTT calculation
                    if ((pkt->seq % 20) == 0) {
                        ack_packet.ack_seq = pkt->seq;
                        g_udp.beginPacket(g_udp.remoteIP(), g_udp.remotePort());
                        g_udp.write(reinterpret_cast<const uint8_t*>(&ack_packet), sizeof(AckPacket));
                        g_udp.endPacket();
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Setup
// -------------------------------------------------------------------------------------
void setup() {
    // 1. Set CPU frequency to 80 MHz (Lowest possible clock for ESP32-S3 Wi-Fi)
    setCpuFrequencyMhz(80);

    Serial.begin(115200);
    delay(50);

    Serial.println("\n=======================================================");
    Serial.println("  APEX-F6X: ESP32-S3 LIGHTWEIGHT & COOL CONTROLLER     ");
    Serial.printf ("  CPU: %u MHz | Wi-Fi 802.11n | Dual BTS7960          \n", getCpuFrequencyMhz());
    Serial.printf ("  Internal Die Temp: %.1f C                           \n", temperatureRead());
    Serial.println("=======================================================");

    // 2. Initialize BTS7960 Drivers
    init_motor_drivers();

    // 3. Ultra-Low Thermal Wi-Fi Access Point Configuration
    WiFi.mode(WIFI_AP);
    IPAddress local_ip(192, 168, 4, 1);
    IPAddress gateway(192, 168, 4, 1);
    IPAddress subnet(255, 255, 255, 0);
    WiFi.softAPConfig(local_ip, gateway, subnet);

    bool ap_ok = WiFi.softAP(AP_SSID, AP_PASS, AP_CHANNEL, 0, AP_MAX_CONN);
    if (ap_ok) {
        Serial.printf("[Wi-Fi] AP Ready: %s (Ch %d) IP: 192.168.4.1\n", AP_SSID, AP_CHANNEL);
    }

    // Capped RF TX power: 28 (7.0 dBm = 5 mW)
    esp_wifi_set_max_tx_power(28);

    // Disable modem sleep to prevent packet buffering jitter
    esp_wifi_set_ps(WIFI_PS_NONE);

    // Lock bandwidth to HT20 for maximum SNR stability
    esp_wifi_set_bandwidth(WIFI_IF_AP, WIFI_BW_HT20);

    // 4. Start UDP Server
    g_udp.begin(UDP_PORT);
    g_last_packet_time = millis();

    Serial.println("[SYSTEM] Robot Ready (NeoPixel Disabled, 80MHz Cool Mode, 750ms Watchdog).");
}

// -------------------------------------------------------------------------------------
// High-Frequency 333 Hz Control Loop (3 ms tick)
// -------------------------------------------------------------------------------------
void loop() {
    uint32_t now = millis();

    // 1. Drain UDP Packets
    process_udp_packets();

    // 2. Thread-Safe State Read
    int16_t cur_target_left  = 0;
    int16_t cur_target_right = 0;
    uint16_t cur_aux         = 0;
    uint32_t last_pkt        = 0;

    portENTER_CRITICAL(&g_mux);
    cur_target_left  = g_target_left_pwm;
    cur_target_right = g_target_right_pwm;
    cur_aux          = g_target_aux_packed;
    last_pkt         = g_last_packet_time;
    portEXIT_CRITICAL(&g_mux);

    // 3. Hardware Failsafe Watchdog (750 ms cutoff)
    // Rides through momentary 100-300 ms Android Wi-Fi scan pauses without shock/jerk
    if ((now - last_pkt) > FAILSAFE_MS) {
        if (!g_failsafe_active) {
            portENTER_CRITICAL(&g_mux);
            g_failsafe_active = true;
            portEXIT_CRITICAL(&g_mux);
            Serial.printf("[FAILSAFE] Signal timeout (>%ums). Smooth brake engaged.\n", FAILSAFE_MS);
        }
        // Smoothly ramp down to zero rather than abruptly slamming enable pins
        cur_target_left  = 0;
        cur_target_right = 0;
    }

    // 4. Slew-Rate Smooth Filter: Eliminates motor jerking and back-EMF spikes
    s_smooth_left_pwm  = slew_towards(s_smooth_left_pwm,  cur_target_left,  MAX_PWM_SLEW_PER_TICK);
    s_smooth_right_pwm = slew_towards(s_smooth_right_pwm, cur_target_right, MAX_PWM_SLEW_PER_TICK);

    // Enable drivers if either motor is commanded; disable only when fully at rest
    if (abs(s_smooth_left_pwm) > 0 || abs(s_smooth_right_pwm) > 0 || !g_failsafe_active) {
        set_driver_enables(true);
    } else {
        set_driver_enables(false);
    }

    apply_left_motor(s_smooth_left_pwm);
    apply_right_motor(s_smooth_right_pwm);
    apply_aux_channels(cur_aux);

    // 5. Periodic Diagnostic Telemetry (every 3 seconds on Serial)
    static uint32_t s_last_diag = 0;
    if (now - s_last_diag >= 3000) {
        s_last_diag = now;
        Serial.printf("[STATUS] DieTemp: %.1f C | RxPkts: %u | Left: %d | Right: %d | Failsafe: %s\n",
            temperatureRead(), g_rx_packet_count, s_smooth_left_pwm, s_smooth_right_pwm,
            g_failsafe_active ? "ACTIVE" : "OK");
    }

    // 3 ms deterministic loop tick (~333 Hz control rate)
    delay(3);
}
