package com.qgyun.hltgq.hltgqsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HltgqSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(HltgqSystemApplication.class, args);
    }

}
