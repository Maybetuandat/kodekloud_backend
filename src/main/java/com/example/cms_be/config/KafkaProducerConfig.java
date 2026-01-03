package com.example.cms_be.config;


import com.example.cms_be.dto.lab.LabSessionCleanupRequest;
import com.example.cms_be.dto.lab.LabTestRequest;
import com.example.cms_be.dto.lab.UserLabSessionRequest;
import com.example.cms_be.dto.lab.ValidationRequest;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    private Map<String, Object> getCommonProducerProps() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return configProps;
    }

    @Bean
    public ProducerFactory<String, LabTestRequest> labTestRequestProducerFactory() {
        return new DefaultKafkaProducerFactory<>(getCommonProducerProps());
    }

    @Bean
    public KafkaTemplate<String, LabTestRequest> kafkaTemplate() {
        return new KafkaTemplate<>(labTestRequestProducerFactory());
    }

    @Bean
    public ProducerFactory<String, UserLabSessionRequest> userLabSessionRequestProducerFactory() {
        return new DefaultKafkaProducerFactory<>(getCommonProducerProps());
    }

    @Bean
    public KafkaTemplate<String, UserLabSessionRequest> userLabSessionKafkaTemplate() {
        return new KafkaTemplate<>(userLabSessionRequestProducerFactory());
    }

    @Bean
    public ProducerFactory<String, ValidationRequest> validationRequestProducerFactory() {
        return new DefaultKafkaProducerFactory<>(getCommonProducerProps());
    }
    @Bean
    public KafkaTemplate<String, ValidationRequest> validationRequestKafkaTemplate() {
        return new KafkaTemplate<>(validationRequestProducerFactory());
    }
     @Bean
    public ProducerFactory<String, LabSessionCleanupRequest> cleanupRequestProducerFactory() {
        return new DefaultKafkaProducerFactory<>(getCommonProducerProps());
    }

    @Bean
    public KafkaTemplate<String, LabSessionCleanupRequest> cleanupRequestKafkaTemplate() {
        return new KafkaTemplate<>(cleanupRequestProducerFactory());
    }
}