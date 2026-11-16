/*
 * =====================================================================================
 *  Project: robosoccer-f6x
 *  File:    udp_engine.cpp
 *  Package: com.anik.f6xcontroller.transport
 *  Author:  Tanvir / Anik
 *
 *  Ultra-Low-Latency POSIX C++ NDK UDP Engine:
 *  - Dedicated high-priority native POSIX thread
 *  - IPTOS_LOWDELAY (0x10) socket flag for minimum queuing delay
 *  - Non-blocking I/O (O_NONBLOCK)
 *  - Fixed 12-Byte Little-Endian binary protocol at deterministic 100 Hz (10 ms)
 *  - Asynchronous ACK tracking (0x55AA) & RTT estimation
 *  - 3x Zero-Speed Stop Packets on shutdown / pause
 * =====================================================================================
 */

#include <jni.h>
#include <string>
#include <cstring>
#include <atomic>
#include <thread>
#include <chrono>
#include <mutex>
#include <map>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <unistd.h>
#include <fcntl.h>
#include <pthread.h>
#include <android/log.h>

#define LOG_TAG "F6X_NativeUDP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#pragma pack(push, 1)
struct ControlPacket {
    uint16_t magic;       // 0xAA55
    uint16_t seq;         // Rolling sequence counter (0..65535)
    int16_t  left_pwm;    // Left motor PWM (-255..+255)
    int16_t  right_pwm;   // Right motor PWM (-255..+255)
    uint16_t aux_packed;  // Packed aux channels (CH5..CH10)
    uint16_t crc16;       // CRC-16 over bytes 0..9 (Poly 0xA001)
};

struct AckPacket {
    uint16_t magic;       // 0x55AA
    uint16_t ack_seq;     // Sequence number being acknowledged
};
#pragma pack(pop)

static uint16_t calculate_crc16(const uint8_t *data, size_t len) {
    uint16_t crc = 0xFFFF;
    for (size_t i = 0; i < len; i++) {
        crc ^= static_cast<uint16_t>(data[i]);
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

class UdpEngine {
public:
    static UdpEngine& getInstance() {
        static UdpEngine instance;
        return instance;
    }

    bool init(const std::string& ip, int port) {
        std::lock_guard<std::mutex> lock(config_mutex_);
        target_ip_ = ip;
        target_port_ = port;
        LOGI("UDP Engine configured target: %s:%d", target_ip_.c_str(), target_port_);
        return true;
    }

    void setTarget(const std::string& ip, int port) {
        std::lock_guard<std::mutex> lock(config_mutex_);
        target_ip_ = ip;
        target_port_ = port;
        updateSockaddr();
    }

    bool start() {
        if (running_.load()) {
            LOGW("UDP Engine is already running.");
            return true;
        }

        // 1. Create UDP socket
        sockfd_ = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
        if (sockfd_ < 0) {
            LOGE("Failed to create UDP socket: %s", strerror(errno));
            return false;
        }

        // 2. Set IPTOS_LOWDELAY (0x10) for minimum delay
        int tos = 0x10; // IPTOS_LOWDELAY
        if (setsockopt(sockfd_, IPPROTO_IP, IP_TOS, &tos, sizeof(tos)) < 0) {
            LOGW("setsockopt IP_TOS IPTOS_LOWDELAY failed: %s", strerror(errno));
        }

        // 3. Set non-blocking mode
        int flags = fcntl(sockfd_, F_GETFL, 0);
        if (flags >= 0) {
            fcntl(sockfd_, F_SETFL, flags | O_NONBLOCK);
        }

        // 4. Increase socket send/receive buffer sizes
        int buf_size = 64 * 1024;
        setsockopt(sockfd_, SOL_SOCKET, SO_SNDBUF, &buf_size, sizeof(buf_size));
        setsockopt(sockfd_, SOL_SOCKET, SO_RCVBUF, &buf_size, sizeof(buf_size));

        updateSockaddr();

        running_.store(true);
        worker_thread_ = std::thread(&UdpEngine::threadLoop, this);

        // 5. Attempt to elevate thread priority
        pthread_t native_handle = worker_thread_.native_handle();
        struct sched_param param;
        param.sched_priority = sched_get_priority_max(SCHED_FIFO);
        if (param.sched_priority > 0) {
            pthread_setschedparam(native_handle, SCHED_FIFO, &param);
        }

        LOGI("UDP Engine started successfully at 100 Hz (10 ms)");
        return true;
    }

    void stop() {
        if (!running_.load()) {
            return;
        }
        running_.store(false);

        // Transmit 3 zero-speed stop packets before closing socket
        sendStopPackets();

        if (worker_thread_.joinable()) {
            worker_thread_.join();
        }

        if (sockfd_ >= 0) {
            close(sockfd_);
            sockfd_ = -1;
        }

        LOGI("UDP Engine stopped gracefully. Stop packets sent.");
    }

    void updateControls(int16_t left, int16_t right, uint16_t aux) {
        atomic_left_pwm_.store(left, std::memory_order_relaxed);
        atomic_right_pwm_.store(right, std::memory_order_relaxed);
        atomic_aux_packed_.store(aux, std::memory_order_relaxed);
    }

    uint64_t getTxCount() const { return tx_count_.load(std::memory_order_relaxed); }
    uint64_t getAckCount() const { return ack_count_.load(std::memory_order_relaxed); }
    float getLastRttMs() const { return last_rtt_ms_.load(std::memory_order_relaxed); }

private:
    UdpEngine() : sockfd_(-1), running_(false), seq_(0),
                  target_ip_("192.168.4.1"), target_port_(4210),
                  atomic_left_pwm_(0), atomic_right_pwm_(0), atomic_aux_packed_(0),
                  tx_count_(0), ack_count_(0), last_rtt_ms_(0.0f) {
        std::memset(&target_addr_, 0, sizeof(target_addr_));
    }

    ~UdpEngine() {
        stop();
    }

    void updateSockaddr() {
        std::lock_guard<std::mutex> lock(config_mutex_);
        std::memset(&target_addr_, 0, sizeof(target_addr_));
        target_addr_.sin_family = AF_INET;
        target_addr_.sin_port = htons(static_cast<uint16_t>(target_port_));
        inet_pton(AF_INET, target_ip_.c_str(), &target_addr_.sin_addr);
    }

    void sendStopPackets() {
        if (sockfd_ < 0) return;

        ControlPacket stop_pkt;
        stop_pkt.magic = 0xAA55;
        stop_pkt.left_pwm = 0;
        stop_pkt.right_pwm = 0;
        stop_pkt.aux_packed = 0;

        for (int i = 0; i < 3; ++i) {
            stop_pkt.seq = seq_.fetch_add(1, std::memory_order_relaxed);
            stop_pkt.crc16 = calculate_crc16(reinterpret_cast<const uint8_t*>(&stop_pkt), 10);

            sendto(sockfd_, &stop_pkt, sizeof(stop_pkt), 0,
                   reinterpret_cast<struct sockaddr*>(&target_addr_), sizeof(target_addr_));
            tx_count_.fetch_add(1, std::memory_order_relaxed);
            usleep(2500); // 2.5 ms spacing
        }
    }

    void threadLoop() {
        using Clock = std::chrono::steady_clock;
        const std::chrono::microseconds interval(10000); // 10 ms = 100 Hz

        ControlPacket pkt;
        pkt.magic = 0xAA55;

        // Circular buffer or map for tracking RTT of sent sequence numbers
        std::map<uint16_t, Clock::time_point> tx_timestamps;

        auto next_tick = Clock::now();

        while (running_.load(std::memory_order_relaxed)) {
            next_tick += interval;

            // 1. Prepare packet
            uint16_t current_seq = seq_.fetch_add(1, std::memory_order_relaxed);
            pkt.seq = current_seq;
            pkt.left_pwm = atomic_left_pwm_.load(std::memory_order_relaxed);
            pkt.right_pwm = atomic_right_pwm_.load(std::memory_order_relaxed);
            pkt.aux_packed = atomic_aux_packed_.load(std::memory_order_relaxed);
            pkt.crc16 = calculate_crc16(reinterpret_cast<const uint8_t*>(&pkt), 10);

            auto tx_time = Clock::now();
            if (tx_timestamps.size() > 100) {
                tx_timestamps.erase(tx_timestamps.begin());
            }
            tx_timestamps[current_seq] = tx_time;

            // 2. Transmit via POSIX sendto
            ssize_t sent = sendto(sockfd_, &pkt, sizeof(pkt), 0,
                                  reinterpret_cast<struct sockaddr*>(&target_addr_), sizeof(target_addr_));
            if (sent == sizeof(pkt)) {
                tx_count_.fetch_add(1, std::memory_order_relaxed);
            }

            // 3. Non-blocking recvfrom for 4-byte ACKs (0x55AA)
            AckPacket ack;
            struct sockaddr_in from_addr;
            socklen_t from_len = sizeof(from_addr);

            while (true) {
                ssize_t recvd = recvfrom(sockfd_, &ack, sizeof(ack), MSG_DONTWAIT,
                                         reinterpret_cast<struct sockaddr*>(&from_addr), &from_len);
                if (recvd == sizeof(AckPacket)) {
                    if (ack.magic == 0x55AA) {
                        ack_count_.fetch_add(1, std::memory_order_relaxed);
                        auto it = tx_timestamps.find(ack.ack_seq);
                        if (it != tx_timestamps.end()) {
                            auto rtt_dur = std::chrono::duration_cast<std::chrono::microseconds>(Clock::now() - it->second);
                            last_rtt_ms_.store(rtt_dur.count() / 1000.0f, std::memory_order_relaxed);
                            tx_timestamps.erase(it);
                        }
                    }
                } else {
                    break; // No more incoming packets in buffer
                }
            }

            // 4. Deterministic sleep until next 10 ms boundary
            auto now = Clock::now();
            if (next_tick > now) {
                std::this_thread::sleep_until(next_tick);
            } else {
                // If tick overshot, resync next_tick to prevent burst accumulation
                next_tick = now;
            }
        }
    }

    int sockfd_;
    std::atomic<bool> running_;
    std::atomic<uint16_t> seq_;
    std::thread worker_thread_;

    std::mutex config_mutex_;
    std::string target_ip_;
    int target_port_;
    struct sockaddr_in target_addr_;

    std::atomic<int16_t> atomic_left_pwm_;
    std::atomic<int16_t> atomic_right_pwm_;
    std::atomic<uint16_t> atomic_aux_packed_;

    std::atomic<uint64_t> tx_count_;
    std::atomic<uint64_t> ack_count_;
    std::atomic<float> last_rtt_ms_;
};

// -------------------------------------------------------------------------------------
// JNI Exported Functions
// -------------------------------------------------------------------------------------
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_anik_f6xcontroller_transport_NativeUdpBridge_initNative(
        JNIEnv *env, jobject /* thiz */, jstring ip, jint port) {
    const char *native_ip = env->GetStringUTFChars(ip, nullptr);
    std::string ip_str(native_ip ? native_ip : "192.168.4.1");
    if (native_ip) env->ReleaseStringUTFChars(ip, native_ip);

    return static_cast<jboolean>(UdpEngine::getInstance().init(ip_str, port));
}

JNIEXPORT jboolean JNICALL
Java_com_anik_f6xcontroller_transport_NativeUdpBridge_startNative(
        JNIEnv * /* env */, jobject /* thiz */) {
    return static_cast<jboolean>(UdpEngine::getInstance().start());
}

JNIEXPORT void JNICALL
Java_com_anik_f6xcontroller_transport_NativeUdpBridge_stopNative(
        JNIEnv * /* env */, jobject /* thiz */) {
    UdpEngine::getInstance().stop();
}

JNIEXPORT void JNICALL
Java_com_anik_f6xcontroller_transport_NativeUdpBridge_updateControlsNative(
        JNIEnv * /* env */, jobject /* thiz */,
        jshort left_pwm, jshort right_pwm, jint aux_packed) {
    UdpEngine::getInstance().updateControls(
            static_cast<int16_t>(left_pwm),
            static_cast<int16_t>(right_pwm),
            static_cast<uint16_t>(aux_packed & 0xFFFF));
}

JNIEXPORT jlong JNICALL
Java_com_anik_f6xcontroller_transport_NativeUdpBridge_getTxCountNative(
        JNIEnv * /* env */, jobject /* thiz */) {
    return static_cast<jlong>(UdpEngine::getInstance().getTxCount());
}

JNIEXPORT jlong JNICALL
Java_com_anik_f6xcontroller_transport_NativeUdpBridge_getAckCountNative(
        JNIEnv * /* env */, jobject /* thiz */) {
    return static_cast<jlong>(UdpEngine::getInstance().getAckCount());
}

JNIEXPORT jfloat JNICALL
Java_com_anik_f6xcontroller_transport_NativeUdpBridge_getLastRttMsNative(
        JNIEnv * /* env */, jobject /* thiz */) {
    return static_cast<jfloat>(UdpEngine::getInstance().getLastRttMs());
}

JNIEXPORT void JNICALL
Java_com_anik_f6xcontroller_transport_NativeUdpBridge_setTargetNative(
        JNIEnv *env, jobject /* thiz */, jstring ip, jint port) {
    const char *native_ip = env->GetStringUTFChars(ip, nullptr);
    std::string ip_str(native_ip ? native_ip : "192.168.4.1");
    if (native_ip) env->ReleaseStringUTFChars(ip, native_ip);

    UdpEngine::getInstance().setTarget(ip_str, port);
}

} // extern "C"
