package com.cloud.computing.filesharingapp;

import com.cloud.computing.filesharingapp.service.FileService;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Main Spring Boot application class for the File Sharing Application.
 * 
 * <p>This application provides secure file upload, download, and sharing capabilities
 * with user authentication, email verification, and comprehensive security features.
 * 
 * <p>Key features include:
 * <ul>
 *   <li>JWT-based authentication and authorization</li>
 *   <li>Email verification for user registration</li>
 *   <li>Rate limiting and security auditing</li>
 *   <li>File upload/download with user isolation</li>
 *   <li>Automated log file cleanup</li>
 * </ul>
 * 
 * @author File Sharing App Team
 * @version 1.0
 * @since 1.0
 */
@SpringBootApplication
@EnableScheduling
public class FilesharingappApplication implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(FilesharingappApplication.class);

	@Autowired(required = false)
	private FileService fileService;

	static {
		// Load environment variables from .env file during class initialization
		loadEnvironmentVariables();
	}

	/**
	 * Main entry point for the Spring Boot application.
	 * 
	 * @param args command line arguments passed to the application
	 */
	public static void main(String[] args) {
		SpringApplication.run(FilesharingappApplication.class, args);
	}

	/**
	 * Executes initialization tasks after the Spring context is loaded.
	 * 
	 * <p>This method performs the following startup tasks:
	 * <ul>
	 *   <li>Initializes the file service and creates upload directories</li>
	 *   <li>Performs cleanup of old log files to manage disk space</li>
	 * </ul>
	 * 
	 * @param args command line arguments (not used)
	 * @throws Exception if initialization fails
	 */
	@Override
	public void run(String... args) throws Exception {
		// Initialize file service and create necessary directories
		if (fileService != null) {
			fileService.init();
		}
		
		// Clean up old log files on startup to manage disk space
		cleanupLogFiles();
	}

	/**
	 * Loads environment variables from the .env file into system properties.
	 * 
	 * <p>This method attempts to load configuration from a .env file in the current
	 * directory. If the file is missing or malformed, the application continues
	 * with default configuration values. System properties take precedence over
	 * .env file values to allow runtime overrides.
	 */
	private static void loadEnvironmentVariables() {
		try {
			Dotenv dotenv = Dotenv.configure()
					.directory(".")
					.ignoreIfMalformed()
					.ignoreIfMissing()
					.load();

			// Set system properties from .env file
			dotenv.entries().forEach(entry -> {
				if (System.getProperty(entry.getKey()) == null) {
					System.setProperty(entry.getKey(), entry.getValue());
				}
			});

			logger.info("Environment variables loaded from .env file");
		} catch (Exception e) {
			logger.warn("Could not load .env file: {}", e.getMessage());
		}
	}

	/**
	 * Performs cleanup of old log files to prevent excessive disk usage.
	 * 
	 * <p>This method:
	 * <ul>
	 *   <li>Creates the logs directory if it doesn't exist</li>
	 *   <li>Identifies all log files (*.log, *.log.gz, *.log.N)</li>
	 *   <li>Keeps only the 10 most recent log files</li>
	 *   <li>Deletes older log files to manage disk space</li>
	 * </ul>
	 * 
	 * <p>The cleanup is performed safely with proper error handling to ensure
	 * application startup is not affected by log management issues.
	 */
	private void cleanupLogFiles() {
		try {
			String logPath = System.getProperty("logging.file.path", "logs");
			Path logsDir = Paths.get(logPath);
			
			if (!Files.exists(logsDir)) {
				logger.info("Logs directory does not exist, creating: {}", logPath);
				Files.createDirectories(logsDir);
				return;
			}

			// Get all log files
			File[] logFiles = logsDir.toFile().listFiles((dir, name) -> 
				name.endsWith(".log") || name.endsWith(".log.gz") || name.matches(".*\\.log\\.\\d+"));

			if (logFiles != null && logFiles.length > 0) {
				// Sort by last modified date (oldest first)
				Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified));

				int maxLogFiles = 10; // Keep only the 10 most recent log files
				int filesToDelete = Math.max(0, logFiles.length - maxLogFiles);

				for (int i = 0; i < filesToDelete; i++) {
					try {
						Files.delete(logFiles[i].toPath());
						logger.info("Deleted old log file: {}", logFiles[i].getName());
					} catch (IOException e) {
						logger.warn("Could not delete log file {}: {}", logFiles[i].getName(), e.getMessage());
					}
				}

				if (filesToDelete > 0) {
					logger.info("Cleaned up {} old log files", filesToDelete);
				} else {
					logger.info("No log files need cleanup (found {} files)", logFiles.length);
				}
			} else {
				logger.info("No log files found in directory: {}", logPath);
			}

		} catch (Exception e) {
			logger.error("Error during log cleanup: {}", e.getMessage(), e);
		}
	}
}
