package com.example.cms_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.cms_be.model.Lab;

public interface LabRepository extends JpaRepository<Lab, Integer> {
    /**
     * @param keyword Chuỗi ký tự để tìm trong name.
     * @param isActive Trạng thái active của Lab (true hoặc false).
     * @param courseId ID của Course để lọc Lab.
     * @param pageable Thông tin phân trang.
     * @return Một trang (Page) các Lab phù hợp.
     */
    @Query("SELECT l FROM Lab l WHERE " +
            "(:courseId IS NULL OR l.course.id = :courseId) AND " +
            "(:isActive IS NULL OR l.isActive = :isActive) AND " +
            "(:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(l.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Lab> findWithFilters(
            @Param("keyword") String keyword,
            @Param("isActive") Boolean isActive,
            @Param("courseId") Integer courseId,
            Pageable pageable
    );
}