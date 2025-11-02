package com.example.cms_be.handler;

import com.example.cms_be.model.UserLabSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class LabTimerHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(LabTimerHandler.class);

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);

    // Map<labSessionId, Tác vụ timer ĐANG CHẠY>
    private final Map<String, ScheduledFuture<?>> runningTimers = new ConcurrentHashMap<>();

    // Map<labSessionId, WebSocket đang CHỜ lab READY> (Client kết nối trước)
    private final Map<String, WebSocketSession> pendingSessions = new ConcurrentHashMap<>();

    // Map<labSessionId, Lab đã READY đang CHỜ client kết nối> (Backend xong trước)
    private final Map<String, UserLabSession> readySessions = new ConcurrentHashMap<>();

    public LabTimerHandler() {
        // Constructor mặc định
    }

    /**
     * Được gọi khi client WebSocket kết nối (từ React).
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String labSessionId = getLabSessionId(session);
        if (labSessionId == null) {
            log.warn("WebSocket connection attempt without labSessionId. Closing.");
            session.close(CloseStatus.BAD_DATA.withReason("Missing labSessionId"));
            return;
        }

        // 1. Kiểm tra xem lab này đã "READY" (backend xong trước) chưa?
        UserLabSession alreadyReadySession = readySessions.remove(labSessionId);

        if (alreadyReadySession != null) {
            // TRƯỜNG HỢP 1: Backend đã xong. Client kết nối muộn.
            log.info("[Session {}] Client connected to an ALREADY READY lab. Starting timer immediately.", labSessionId);
            // Kích hoạt timer ngay lập tức
            startTimerTask(session, alreadyReadySession.getLab().getEstimatedTime(), labSessionId);
        } else {
            // TRƯỜNG HỢP 2: Backend chưa xong. Client kết nối sớm (bình thường).
            log.info("[Session {}] Client connected. Placing in PENDING to wait for lab readiness.", labSessionId);
            pendingSessions.put(labSessionId, session);
        }
    }

    public void startTimerForSession(UserLabSession userLabSession) {
        String labSessionId = String.valueOf(userLabSession.getId());

        // 1. Kiểm tra xem client đã ở trong "phòng chờ" (pending) chưa?
        WebSocketSession pendingClient = pendingSessions.remove(labSessionId);

        if (pendingClient != null) {
            // TRƯỜNG HỢP 1: Client đã kết nối và đang chờ (bình thường).
            log.info("[Session {}] Lab is READY. Client was pending. Starting timer.", labSessionId);
            startTimerTask(pendingClient, userLabSession.getLab().getEstimatedTime(), labSessionId);
        } else {
            // TRƯỜNG HỢP 2: Client chưa kết nối (Race condition).
            log.warn("[Session {}] Lab is READY, but no pending client found. Placing in READY queue.", labSessionId);
            // "Ghi chú" lại là lab này đã sẵn sàng để chờ client kết nối
            readySessions.put(labSessionId, userLabSession);
        }
    }

    /**
     * Hàm helper nội bộ để khởi động tác vụ đếm ngược.
     */
    private void startTimerTask(WebSocketSession wsSession, int labTimeInMinutes, String labSessionId) {
        if (runningTimers.containsKey(labSessionId)) {
            log.warn("[Session {}] Timer start requested, but it is already running.", labSessionId);
            return;
        }

        log.info("[Session {}] Starting timer task ({} minutes)", labSessionId, labTimeInMinutes);
        LabTimerTask timerTask = new LabTimerTask(wsSession, labTimeInMinutes);
        ScheduledFuture<?> scheduledFuture = executorService.scheduleAtFixedRate(timerTask, 0, 1, TimeUnit.SECONDS);

        runningTimers.put(labSessionId, scheduledFuture);
    }


    /**
     * Được gọi khi client ngắt kết nối.
     * Dọn dẹp tất cả các Map để tránh memory leak.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String labSessionId = getLabSessionId(session);
        if (labSessionId == null) {
            return;
        }

        log.info("[Session {}] WebSocket connection closed. Cleaning up all maps.", labSessionId, status.getReason());

        // Dọn dẹp cả 3 map
        pendingSessions.remove(labSessionId);
        readySessions.remove(labSessionId);

        ScheduledFuture<?> scheduledFuture = runningTimers.remove(labSessionId);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true); // Hủy tác vụ đếm ngược
        }
    }

    /**
     * Hàm này được gọi khi lab setup THẤT BẠI.
     * Nó sẽ tìm client (nếu đang chờ) và báo lỗi.
     */
    public void notifySessionFailed(String labSessionId, String reason) {
        // Lấy session ra khỏi phòng chờ
        WebSocketSession wsSession = pendingSessions.remove(labSessionId);

        if (wsSession != null && wsSession.isOpen()) {
            try {
                log.warn("[Session {}] Notifying client of setup failure: {}", labSessionId, reason);
                wsSession.sendMessage(new TextMessage("SETUP_FAILED"));
                wsSession.close(CloseStatus.NORMAL.withReason(reason));
            } catch (IOException e) {
                log.error("[Session {}] Error sending failure message to client: {}", labSessionId, e.getMessage());
            }
        }

        // Cũng dọn dẹp các map khác
        readySessions.remove(labSessionId);
        runningTimers.remove(labSessionId);
    }


    private String getLabSessionId(WebSocketSession session) {
        return (String) session.getAttributes().get("labSessionId");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.warn("Received unexpected message from client: {}", message.getPayload());
    }
}