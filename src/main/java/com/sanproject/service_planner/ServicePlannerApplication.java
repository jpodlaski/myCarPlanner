package com.sanproject.service_planner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot entry point — starts the embedded Tomcat server and component scan. */
@SpringBootApplication
public class ServicePlannerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServicePlannerApplication.class, args);
	}

}
