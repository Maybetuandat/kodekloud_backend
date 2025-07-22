package com.example.cms_be.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cms_be.model.Lab;
import com.example.cms_be.service.LabService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;



@Slf4j
@RestController
@RequestMapping("/api/lab")
@RequiredArgsConstructor
public class LabController {
     private final LabService labService; 

    @GetMapping
    public ResponseEntity<List<Lab>> getAllLabs(@RequestParam (required = false) Boolean isActivate) {
         List<Lab> labs =  new ArrayList<>();

         if(isActivate != null)
         {
                labs = labService.getLabsByActivateStatus(isActivate);
         }
         else
         {
                labs = labService.getAllLabs();
         }
        
         return ResponseEntity.ok(labs);
    }

    @PostMapping()
    public ResponseEntity<?> createLab(@Valid @RequestBody Lab lab, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            // Xử lý lỗi validation
            Map<String, String> errors = new HashMap<>();
            bindingResult.getFieldErrors().forEach(error -> 
                errors.put(error.getField(), error.getDefaultMessage())
            );
            return ResponseEntity.badRequest().body(errors);
        }
    
        Lab createdLab = labService.createLab(lab);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdLab);
    }
    

}