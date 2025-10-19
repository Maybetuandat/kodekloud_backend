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


    public Category createCategory(Category category) {

        log.info("Creating category: {}", category);
          Category createCategory = new Category();
        try {
          
            createCategory = categoryRepository.save(category);
            log.info("Category created successfully with ID: {}", createCategory.getId());
        } catch (Exception e) {
            log.error("Error creating category: {}", e.getMessage());
            throw new RuntimeException("Failed to create category", e);
        }
        return createCategory;
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Category getCategoryById(Integer id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
    }

    public Category updateCategory(Integer id, Category updatedCategory) {

        try {
            var existingCategory = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));

            existingCategory.setTitle(updatedCategory.getTitle());
            existingCategory.setDescription(updatedCategory.getDescription());
            existingCategory.setIsActive(updatedCategory.getIsActive());

            return categoryRepository.save(existingCategory);
        } catch (Exception e) {
            log.error("Error updating category: {}", e.getMessage());
            throw new RuntimeException("Failed to update category", e);
        }
    }


    public Boolean deleteCategory(Integer id) {
        try {
            var existingCategory = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
            categoryRepository.delete(existingCategory);
            return true;
        } catch (Exception e) {
            log.error("Error deleting category: {}", e.getMessage());
            throw new RuntimeException("Failed to delete category", e);
        }
    }


}
