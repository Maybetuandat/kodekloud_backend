package com.example.cms_be.handler;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

public class LabTimerTask implements Runnable {

    private final WebSocketSession session;
    private int remainingSeconds;

    public LabTimerTask(WebSocketSession session, int totalMinutes) {
        this.session = session;
        this.remainingSeconds = totalMinutes * 60;
    }

    @Override
    public void run() {
        if (remainingSeconds < 0) {
            try {
                session.sendMessage(new TextMessage("TIME_UP"));
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
            throw new RuntimeException("Timer finished");

        } else {
            int minutes = remainingSeconds / 60;
            int seconds = remainingSeconds % 60;
            String timeString = String.format("%02d:%02d", minutes, seconds);

            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(timeString));
                }
            } catch (IOException e) {
                throw new RuntimeException("Client connection error", e);
            }
            remainingSeconds--;
        }
    }
}