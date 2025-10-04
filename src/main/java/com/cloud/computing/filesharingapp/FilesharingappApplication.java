package com.cloud.computing.filesharingapp;

import com.cloud.computing.filesharingapp.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FilesharingappApplication implements CommandLineRunner {

	@Autowired
	private FileService fileService;

	public static void main(String[] args) {
		SpringApplication.run(FilesharingappApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		fileService.init();
	}
}
