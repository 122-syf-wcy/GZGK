package com.gzly;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
@SpringBootApplication
@MapperScan("com.gzly.mapper")
@EnableCaching
public class GzlyApplication {
    public static void main(String[] args) {
        SpringApplication.run(GzlyApplication.class, args);
    }
}
