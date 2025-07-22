// package com.example.cms_be.config;

// import org.springframework.boot.ApplicationArguments;
// import org.springframework.boot.ApplicationRunner;
// import org.springframework.stereotype.Component;

// import com.example.cms_be.service.KubernetesService;

// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;

// @Slf4j
// @Component
// @RequiredArgsConstructor
// public class test implements ApplicationRunner {
    
//     private final KubernetesService kubernetesService;
    
//     @Override
//     public void run(ApplicationArguments args) throws Exception {
//         log.info("=== Starting Kubernetes connectivity test ===");
        
//         try {
//             // Gọi health check để kiểm tra kết nối
//             kubernetesService.performHealthCheck();
//             log.info("=== Kubernetes test completed successfully ===");
//         } catch (Exception e) {
//             log.error("=== Kubernetes test failed ===", e);
//         }
//     }
// }