package com.trilink.app.domain

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * Finds the primary local IPv4 address (e.g., 192.168.x.x or 10.x.x.x) on active network interfaces.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = mutableListOf<String>()

            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (!host.startsWith("127.")) {
                            // Prioritize standard local subnet IPs
                            if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                                return host
                            }
                            candidates.add(host)
                        }
                    }
                }
            }
            return candidates.firstOrNull()
        } catch (e: Exception) {
            return null
        }
    }
}
