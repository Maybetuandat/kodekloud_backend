package com.example.cms_be.controller;

import com.example.cms_be.model.CreateVmRequest;
import com.example.cms_be.service.KubernetesService;
import com.example.cms_be.service.VMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/vms")
@RequiredArgsConstructor
public class VMController {
    private final VMService vmService;

    @GetMapping("/get-all-pods")
    public ResponseEntity<?> getAllPodsInCluster() {
        try {
            List<Map<String, String>> pods = vmService.getAllPods();
            return ResponseEntity.ok(pods);
        } catch (Exception e) {
            log.error("Error getting all pods: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể lấy danh sách pods: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/create-vm")
    public ResponseEntity<?> createVirtualMachine(@RequestBody CreateVmRequest request) {
        try {
            // Gọi service để tạo DataVolume và VirtualMachine
            vmService.createVirtualMachine(
                    request.name(),
                    request.namespace(),
                    request.imageUrl(),
                    request.storage(),
                    request.memory()
            );

            Map<String, String> response = new HashMap<>();
            response.put("message", "Yêu cầu tạo máy ảo '" + request.name() + "' đã được gửi thành công.");
            response.put("details", "Quá trình tải image và khởi động máy ảo sẽ chạy ngầm. Vui lòng kiểm tra trạng thái sau.");

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (Exception e) {
            log.error("Error creating virtual machine {}: {}", request.name(), e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể tạo máy ảo: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

}
