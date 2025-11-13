package com.example.cms_be.service;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.cms_be.model.Category;
import com.example.cms_be.repository.CategoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryService {


    private final CategoryRepository categoryRepository;


    public List<Category> getAllCategories() {
        try {
            return categoryRepository.findAll();
        } catch (Exception e) {
            log.error("Error fetching categories: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch categories", e);
        }
    }

    public Category createCategory(Category category) {
        try {
            return categoryRepository.save(category);
        } catch (Exception e) {
            log.error("Error creating category: {}", e.getMessage());
            throw new RuntimeException("Failed to create category", e);
        }
    }
    public Category getCategoryById(Integer id) {
        try {
            return categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        } catch (Exception e) {
            log.error("Error fetching category by id: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch category", e);
        }
    }
    public Category updateCategory(Integer id, Category updatedCategory) {
        try {
            Category existingCategory = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));

            existingCategory.setName(updatedCategory.getName());
            existingCategory.setDescriptions(updatedCategory.getDescriptions());

            return categoryRepository.save(existingCategory);
        } catch (Exception e) {
            log.error("Error updating category: {}", e.getMessage());
            throw new RuntimeException("Failed to update category", e);
        }
    }
    public void deleteCategory(Integer id) {
        try {
            Category existingCategory = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
            categoryRepository.delete(existingCategory);
        } catch (Exception e) {
            log.error("Error deleting category: {}", e.getMessage());
            throw new RuntimeException("Failed to delete category", e);
        }
    }

}
