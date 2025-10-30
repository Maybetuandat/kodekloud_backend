package com.example.cms_be.service;

import com.example.cms_be.dto.BackingImageDTO;
import com.google.gson.internal.LinkedTreeMap;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class StorageService {

    private final CustomObjectsApi customApi;

    // Định nghĩa các hằng số cho tài nguyên của Longhorn
    private static final String LONGHORN_GROUP = "longhorn.io";
    private static final String LONGHORN_VERSION = "v1beta2";
    private static final String LONGHORN_NAMESPACE = "longhorn-system";
    private static final String BACKING_IMAGES_PLURAL = "backingimages";

    public List<BackingImageDTO> getAllBackingImages() throws ApiException {
        log.info("Fetching all Longhorn backing images from namespace '{}'", LONGHORN_NAMESPACE);

        try {
            Object rawObject = customApi.listNamespacedCustomObject(
                    LONGHORN_GROUP,
                    LONGHORN_VERSION,
                    LONGHORN_NAMESPACE,
                    BACKING_IMAGES_PLURAL,
                    null,    // pretty: null hoặc "" đều được, để null cho gọn
                    false,   // allowWatchBookmarks: Tắt đi khi không watch
                    null,    // _continue
                    null,    // fieldSelector
                    null,    // labelSelector
                    null,    // limit
                    null,    // resourceVersion
                    null,    // resourceVersionMatch
                    null,    // timeoutSeconds
                    false    // watch: PHẢI LÀ FALSE ĐỂ THỰC HIỆN LIST
            );
            // Chuyển đổi kết quả thô sang danh sách DTO
            return mapToBackingImageDTOs(rawObject);

        } catch (ApiException e) {
            log.error("Kubernetes API Error when listing backing images. Status: {}, Body: {}", e.getCode(), e.getResponseBody(), e);
            throw e; // Ném lại lỗi để Controller có thể bắt và xử lý
        }
    }

    /**
     * Hàm helper để parse kết quả từ CustomObjectsApi
     */
    @SuppressWarnings("unchecked")
    private List<BackingImageDTO> mapToBackingImageDTOs(Object rawObject) {
        List<BackingImageDTO> dtoList = new ArrayList<>();

        // Kết quả trả về là một Map, trong đó key "items" chứa danh sách thật sự
        if (rawObject instanceof Map) {
            Map<String, Object> objectMap = (Map<String, Object>) rawObject;
            List<Object> items = (List<Object>) objectMap.get("items");

            if (items == null) return dtoList;

            for (Object item : items) {
                if (item instanceof Map) {
                    Map<String, Object> itemMap = (Map<String, Object>) item;
                    BackingImageDTO dto = new BackingImageDTO();

                    // Lấy thông tin từ metadata
                    Map<String, Object> metadata = (Map<String, Object>) itemMap.get("metadata");
                    if (metadata != null) {
                        dto.setName((String) metadata.get("name"));
                        dto.setUuid((String) metadata.get("uid"));
                    }

                    // Lấy thông tin từ status
                    Map<String, Object> status = (Map<String, Object>) itemMap.get("status");
                    if (status != null) {
                        // Trạng thái của image
                        dto.setState((String) status.get("currentState"));
                        // Tiến độ download
                        Object progressObj = status.get("progress");
                        if (progressObj instanceof Number) {
                            dto.setDownloadProgress(((Number) progressObj).intValue());
                        }
                        // Kích thước thật sau khi tải xong
                        Object sizeObj = status.get("size");
                        if (sizeObj instanceof Number) {
                            dto.setSize(((Number) sizeObj).longValue());
                        }
                    }

                    // Lấy thông tin từ spec
                    Map<String, Object> spec = (Map<String, Object>) itemMap.get("spec");
                    if (spec != null) {
                        dto.setCreatedFrom((String) spec.get("sourceType")); // "download", "upload", etc.
                    }

                    dtoList.add(dto);
                }
            }
        }
        return dtoList;
    }
}