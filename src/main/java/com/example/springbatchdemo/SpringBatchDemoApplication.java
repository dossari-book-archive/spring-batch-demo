package com.example.springbatchdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpringBatchDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBatchDemoApplication.class, args);
    }
}
