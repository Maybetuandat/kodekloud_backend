package com.example.cms_be.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class BackendIpService {

    @Value("${app.execution-environment:outside-cluster}")
    private String executionEnvironment;

    @Value("${app.backend.static-ip:}")
    private String staticIpOverride;

    private static final List<String> PUBLIC_IP_SERVICES = List.of(
            "https://api.ipify.org",
            "https://icanhazip.com",
            "https://checkip.amazonaws.com"
    );

    /**
     * Lấy danh sách IP addresses của backend để thêm vào NetworkPolicy
     */
    public List<String> getBackendIpAddresses() {
        List<String> ipAddresses = new ArrayList<>();

        try {
            // 1. Static IP override (nếu có)
            if (staticIpOverride != null && !staticIpOverride.trim().isEmpty()) {
                ipAddresses.add(staticIpOverride.trim());
                log.info("Using static backend IP: {}", staticIpOverride);
                return ipAddresses;
            }

            // 2. Nếu chạy outside cluster, cần public IP
            if ("outside-cluster".equalsIgnoreCase(executionEnvironment)) {
                String publicIp = detectPublicIp();
                if (publicIp != null) {
                    ipAddresses.add(publicIp);
                    log.info("Detected public IP: {}", publicIp);
                }
            }

            // // 3. Thêm local IPs case backend chay trong cluster 
            // List<String> localIps = getLocalIps();
            // ipAddresses.addAll(localIps);

         
            ipAddresses = ipAddresses.stream().distinct().toList();

        } catch (Exception e) {
            log.error("Error detecting backend IPs: {}", e.getMessage());
        }

        if (ipAddresses.isEmpty()) {
            log.warn("No backend IP addresses detected!");
        } else {
            log.info("Backend IP addresses: {}", ipAddresses);
        }

        return ipAddresses;
    }

    /**
     * Detect public IP từ external services
     */
    private String detectPublicIp() {
        for (String serviceUrl : PUBLIC_IP_SERVICES) {
            try {
                String ip = fetchIpFromService(serviceUrl);
                if (ip != null && isValidIp(ip)) {
                    log.debug("Got public IP from {}: {}", serviceUrl, ip);
                    return ip;
                }
            } catch (Exception e) {
                log.debug("Failed to get IP from {}: {}", serviceUrl, e.getMessage());
            }
        }
        log.warn("Could not detect public IP from any service");
        return null;
    }

    /**
     * fetch public ip 
     */
    private String fetchIpFromService(String serviceUrl) throws Exception {
        URL url = new URL(serviceUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "CMS-Backend/1.0");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
            return reader.readLine().trim();
        }
    }

        // /**
        //  * Lấy local network IPs
        //  */
        // private List<String> getLocalIps() {
        //     List<String> localIps = new ArrayList<>();
            
        //     try {
        //         List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
                
        //         for (NetworkInterface networkInterface : interfaces) {
        //             if (networkInterface.isLoopback() || !networkInterface.isUp()) {
        //                 continue;
        //             }

        //             List<InetAddress> addresses = Collections.list(networkInterface.getInetAddresses());
                    
        //             for (InetAddress address : addresses) {
        //                 String ip = address.getHostAddress();
                        
        //                 // Chỉ lấy IPv4 và không phải loopback
        //                 if (address.getAddress().length == 4 && !address.isLoopbackAddress()) {
        //                     if (isPrivateIp(ip)) {
        //                         localIps.add(ip);
        //                         log.debug("Found local IP: {} on {}", ip, networkInterface.getName());
        //                     }
        //                 }
        //             }
        //         }
        //     } catch (Exception e) {
        //         log.error("Error getting local IPs: {}", e.getMessage());
        //     }

        //     return localIps;
        // }


    private boolean isValidIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        try {
            InetAddress.getByName(ip.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // /**
    //  * Kiểm tra private IP
    //  */
    // private boolean isPrivateIp(String ip) {
    //     try {
    //         InetAddress address = InetAddress.getByName(ip);
    //         return address.isSiteLocalAddress() || address.isLinkLocalAddress();
    //     } catch (Exception e) {
    //         return false;
    //     }
    // }
}