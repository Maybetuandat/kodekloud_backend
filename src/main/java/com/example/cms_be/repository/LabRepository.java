package com.example.cms_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.cms_be.model.Lab;

public interface LabRepository extends JpaRepository<Lab, String> {

    /**
     * @param search Chuỗi ký tự để tìm trong name, description, và baseImage.
     * @param isActive Trạng thái active của Lab (true hoặc false).
     * @param pageable Thông tin phân trang.
     * @return Một trang (Page) các Lab phù hợp.
     */
    @Query("SELECT l FROM Lab l WHERE " +
            "(:isActive IS NULL OR l.isActive = :isActive) AND " +
            "(:search IS NULL OR :search = '' OR " +
            "(LOWER(l.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(l.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(l.baseImage) LIKE LOWER(CONCAT('%', :search, '%'))))")
    Page<Lab> findWithFilters(
            @Param("search") String search,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );
}