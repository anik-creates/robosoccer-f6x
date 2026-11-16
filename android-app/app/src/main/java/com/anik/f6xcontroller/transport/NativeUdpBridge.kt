package com.anik.f6xcontroller.transport

object NativeUdpBridge {

    init {
        System.loadLibrary("f6x_udp")
    }

    external fun initNative(ip: String, port: Int): Boolean
    external fun startNative(): Boolean
    external fun stopNative()
    external fun updateControlsNative(leftPwm: Short, rightPwm: Short, auxPacked: Int)
    external fun getTxCountNative(): Long
    external fun getAckCountNative(): Long
    external fun getLastRttMsNative(): Float
    external fun setTargetNative(ip: String, port: Int)

    fun init(ip: String = "192.168.4.1", port: Int = 4210): Boolean {
        return initNative(ip, port)
    }

    fun start(): Boolean {
        return startNative()
    }

    fun stop() {
        stopNative()
    }

    fun updateControls(leftPwm: Short, rightPwm: Short, auxPacked: Int) {
        updateControlsNative(leftPwm, rightPwm, auxPacked)
    }

    fun getTxCount(): Long = getTxCountNative()
    fun getAckCount(): Long = getAckCountNative()
    fun getLastRttMs(): Float = getLastRttMsNative()

    fun setTarget(ip: String, port: Int) {
        setTargetNative(ip, port)
    }
}
