package com.media_vault_service.Blob;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class BlobApplication {

	public static void main(String[] args) {
		SpringApplication.run(BlobApplication.class, args);
	}

}
