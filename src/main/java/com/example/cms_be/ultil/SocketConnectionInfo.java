package com.example.cms_be.ultil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class  SocketConnectionInfo {

    private static final String  WEBSOCKET_ENDPOINT = "/ws/pod-logs";
    @Value("${server.port:8080}")
    private  String serverPort;
    

    public   Map<String, Object> createWebSocketConnectionInfo(String podName) {
        Map<String, Object> websocketInfo = new HashMap<>();
        
        // URL kết nối WebSocket với podName parameter
        String wsUrl = String.format("ws://localhost:%s%s?podName=%s", serverPort, WEBSOCKET_ENDPOINT, podName);
        websocketInfo.put("url", wsUrl);
        websocketInfo.put("endpoint", WEBSOCKET_ENDPOINT);
        websocketInfo.put("podName", podName);
        
        // Thông tin connection
        Map<String, String> connectionParams = new HashMap<>();
        connectionParams.put("podName", podName);
        connectionParams.put("type", "log-stream");
        websocketInfo.put("connectionParams", connectionParams);
        
        // Các message types mà client có thể nhận được
        List<String> messageTypes = List.of(
            "start", "info", "warning", "error", "success",
            "step", "step_success", "step_error", 
            "log", "stdout", "stderr", "retry", "connection"
        );
        websocketInfo.put("messageTypes", messageTypes);
        
        // Hướng dẫn kết nối
        Map<String, String> instructions = new HashMap<>();
        instructions.put("connect", "Kết nối tới WebSocket URL: " + wsUrl);
        instructions.put("note", "PodName được truyền qua query parameter, không cần subscribe message");
        instructions.put("autoFilter", "Messages sẽ được tự động filter theo podName");
        websocketInfo.put("instructions", instructions);
        
        // Sample message format mà client sẽ nhận được
        Map<String, Object> sampleMessage = new HashMap<>();
        sampleMessage.put("type", "info");
        sampleMessage.put("message", "Sample log message");
        sampleMessage.put("data", null);
        sampleMessage.put("timestamp", System.currentTimeMillis());
        websocketInfo.put("sampleMessage", sampleMessage);
        
        return websocketInfo;
    }

}
