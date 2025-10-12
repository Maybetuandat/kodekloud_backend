package com.example.cms_be.controller;

import com.example.cms_be.dto.CreateLabSessionRequest;
import com.example.cms_be.model.Lab;
import com.example.cms_be.service.LabService;
import com.example.cms_be.service.VMService;
import io.kubernetes.client.openapi.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/vms")
@RequiredArgsConstructor
public class VMController {
    private final VMService vmService;
    private final LabService labService;

//    @PostMapping("/create-vm")
//    public ResponseEntity<?> createVirtualMachine(@RequestBody String labId) throws Exception {
//        Lab lab = this.labService.getLabById(labId)
//                .orElseThrow(() -> new Exception("Không tìm thấy Lab với ID: " + labId));
//        try {
//            vmService.provisionVmForSession(lab);
//
//            Map<String, String> response = new HashMap<>();
//            response.put("message", "Yêu cầu tạo máy ảo cho lab '" + lab.getName() + "' đã được gửi thành công.");
//            response.put("details", "Quá trình tải image và khởi động máy ảo sẽ chạy ngầm. Vui lòng kiểm tra trạng thái sau.");
//
//            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
//
//        } catch (ApiException e) {
//            log.error("Kubernetes API Error creating VM '{}'. Status Code: {}. Response Body: {}",
//                    lab.getName(), e.getCode(), e.getResponseBody(), e);
//
//            Map<String, String> error = new HashMap<>();
//            error.put("error", "Lỗi từ Kubernetes API.");
//            error.put("details", e.getResponseBody());
//            return ResponseEntity.status(e.getCode()).body(error);
//
//        } catch (Exception e) {
//            log.error("Error creating virtual machine {}: {}", lab.getName(), e.getMessage(), e);
//            Map<String, String> error = new HashMap<>();
//            error.put("error", "Không thể tạo máy ảo: " + e.getMessage());
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
//        }
//    }
}
