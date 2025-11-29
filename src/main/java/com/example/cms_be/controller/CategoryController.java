package com.example.cms_be.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cms_be.model.Category;
import com.example.cms_be.model.Lab;
import com.example.cms_be.service.CategoryService;
import com.example.cms_be.service.LabService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;



@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {
    private final CategoryService categoryService;
    private final LabService labService;

//    @PreAuthorize("hasAuthority('CATEGORY_READ') or hasAuthority('CATEGORY_ALL')")
    @GetMapping("")
    public ResponseEntity<List<Category>> getCategories() {
        try {
            return ResponseEntity.ok(categoryService.getAllCategories());
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

//    @PreAuthorize("hasAuthority('CATEGORY_READ') or hasAuthority('CATEGORY_ALL')")
    @GetMapping("/{id}")
    public ResponseEntity<Category> getCategoryById(@PathVariable Integer id) {
        try {
            Category category = categoryService.getCategoryById(id);
            return ResponseEntity.ok(category);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

//    @PreAuthorize("hasAuthority('CATEGORY_CREATE') or hasAuthority('CATEGORY_ALL')")
    @PostMapping("")
    public ResponseEntity<Category> createCategory(@RequestBody Category category) {
        try {

            Category createdCategory = categoryService.createCategory(category);
            return ResponseEntity.status(201).body(createdCategory);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
    

    @PostMapping("{categoryId}/labs")
    public ResponseEntity<?> createLab(@PathVariable Integer categoryId, @RequestBody Lab lab) {
        try {
            Lab createdLab = labService.createLab(lab, categoryId);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdLab);
        } catch (Exception e) {
            log.error("Error creating lab: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @PatchMapping("/{id}")
    public ResponseEntity<Category> updateCategory(@PathVariable Integer id, @RequestBody Category category
    ) {
        try {
            Category updatedCategory = categoryService.updateCategory(id, category);
            return ResponseEntity.ok(updatedCategory);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Integer id) {
        try {
            categoryService.deleteCategory(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    
    }
}
