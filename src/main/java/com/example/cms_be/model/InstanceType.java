package com.example.cms_be.model;

import java.util.List;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@Table(name = "instance_types")
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class InstanceType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;


    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description")
    private String description;
    @Column(name = "cpu_cores", nullable = false)
    private Integer cpuCores;
    @Column(name = "memory_gb", nullable = false)
    private Integer memoryGb;
    @Column(name = "storage_gb", nullable = false)
    private Integer storageGb;


    @Column(name = "backing_image", nullable =  false)
    private String backingImage;

    @JsonIgnore
    @OneToMany(mappedBy = "instanceType", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Lab> labs;
}
