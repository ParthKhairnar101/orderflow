package com.parth.orderflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class OrderflowApplication {

	public static void main(String[] args) {
        // Keep JDBC session timestamps consistent and avoid unsupported OS timezone aliases.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(OrderflowApplication.class, args);
	}

}
