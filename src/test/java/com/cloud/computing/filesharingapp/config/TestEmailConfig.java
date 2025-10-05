package com.cloud.computing.filesharingapp.config;

import com.cloud.computing.filesharingapp.service.EmailService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TestEmailConfig {

    @Bean
    @Primary
    public EmailService emailService() {
        EmailService mockEmailService = Mockito.mock(EmailService.class);
        
        // Mock the sendVerificationEmail method to do nothing (successful)
        Mockito.doNothing().when(mockEmailService).sendVerificationEmail(Mockito.anyString(), Mockito.anyString());
        
        // Mock isEmailServiceAvailable to return true
        Mockito.when(mockEmailService.isEmailServiceAvailable()).thenReturn(true);
        
        return mockEmailService;
    }
}