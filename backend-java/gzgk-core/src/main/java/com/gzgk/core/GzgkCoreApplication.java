package com.gzgk.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;

/**
 * GZGK Core Service (Java 21 Virtual Threads).
 * Excludes eager DataSource/Redis/Kafka auto-configs by default unless enabled in prod profile
 * so local development runs smoothly with zero local container memory overhead.
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        KafkaAutoConfiguration.class
})
public class GzgkCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(GzgkCoreApplication.class, args);
    }
}
