package com.cloud.computing.filesharingapp.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class LogTestController {
    
    private static final Logger logger = LoggerFactory.getLogger(LogTestController.class);
    
    @GetMapping("/logs")
    public String testLogs() {
        logger.trace("This is a TRACE message");
        logger.debug("This is a DEBUG message");
        logger.info("This is an INFO message");
        logger.warn("This is a WARN message");
        logger.error("This is an ERROR message");
        
        return "Log messages sent! Check your console and log files.";
    }
}