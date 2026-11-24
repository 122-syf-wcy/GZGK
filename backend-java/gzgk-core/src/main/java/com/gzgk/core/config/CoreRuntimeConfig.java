package com.gzgk.core.config;

import com.gzgk.common.compliance.ComplianceTextSanitizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class CoreRuntimeConfig implements WebMvcConfigurer {

    @Bean(destroyMethod = "close")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public ComplianceTextSanitizer complianceTextSanitizer(
            @Value("${gzgk.core.contracts-root:../../contracts}") String contractsRoot
    ) {
        Path path = Path.of(contractsRoot, "compliance-terms.json").normalize();
        return ComplianceTextSanitizer.fromJsonFile(path);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*");
    }

    @Configuration
    @ConditionalOnProperty(name = "gzgk.core.storage-mode", havingValue = "postgres")
    @Import({DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
    static class PostgresInfraConfiguration {
    }

    @Configuration
    @ConditionalOnProperty(name = "gzgk.core.redis-enabled", havingValue = "true")
    @Import(RedisAutoConfiguration.class)
    static class RedisInfraConfiguration {
    }

    @Configuration
    @ConditionalOnProperty(name = "gzgk.core.kafka-enabled", havingValue = "true")
    @Import(KafkaAutoConfiguration.class)
    static class KafkaInfraConfiguration {
    }
}
