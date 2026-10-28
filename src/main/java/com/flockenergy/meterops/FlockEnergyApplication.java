package com.flockenergy.meterops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class FlockEnergyApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlockEnergyApplication.class, args);
    }
}
