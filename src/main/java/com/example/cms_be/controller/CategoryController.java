package com.example.cms_be.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cms_be.model.Category;
import com.example.cms_be.service.CategoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PutMapping;


@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {
        private final CategoryService categoryService;

        @GetMapping("/{id}")
        public ResponseEntity<?> getCategoryById(@PathVariable Integer id) {
            try {
                 Category category = categoryService.getCategoryById(id);
                 return ResponseEntity.ok(category);
            } catch (Exception e) {
                 log.error("Error fetching category by ID {}: {}", id, e.getMessage());
                 return ResponseEntity.status(404).body("Category not found");
            }
         }
       @GetMapping("")
       public ResponseEntity<?> getAllCategories() {
           try {
               List<Category> categories = categoryService.getAllCategories();
               return ResponseEntity.ok(categories);
           } catch (Exception e) {
               log.error("Error fetching all categories: {}", e.getMessage());
               return ResponseEntity.status(500).body("Failed to fetch categories");
           }
       }
       @PostMapping("")
       public ResponseEntity<?> createCategory(@RequestBody Category category) {
           try {
               Category createdCategory = categoryService.createCategory(category);
               return ResponseEntity.ok(createdCategory);
           } catch (Exception e) {
               log.error("Error creating category: {}", e.getMessage());
               return ResponseEntity.status(500).body("Failed to create category");
           }
       }

       @PutMapping("/{id}")
       public ResponseEntity<?> updateCategory(@PathVariable Integer id, @RequestBody Category category) {
           try {
               Category updatedCategory = categoryService.updateCategory(id, category);
               return ResponseEntity.ok(updatedCategory);
           } catch (Exception e) {
               log.error("Error updating category with ID {}: {}", id, e.getMessage());
               return ResponseEntity.status(404).body("Category not found");
           }
       }
       @DeleteMapping("/{id}")
       public ResponseEntity<?> deleteCategory(@PathVariable Integer id) {
           try {
               Boolean deleted = categoryService.deleteCategory(id);
               if (deleted) {
                   return ResponseEntity.ok("Category deleted successfully");
               } else {
                   return ResponseEntity.status(404).body("Category not found");
               }
           } catch (Exception e) {
               log.error("Error deleting category with ID {}: {}", id, e.getMessage());
               return ResponseEntity.status(500).body("Failed to delete category");
           }
       }

}
