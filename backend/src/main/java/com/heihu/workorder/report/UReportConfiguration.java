package com.heihu.workorder.report;

import com.bstek.ureport.console.UReportServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

/**
 * Registers UReport2 inside the existing Spring Boot web application.
 * No second service or container is needed.
 */
@Configuration
@ImportResource("classpath:ureport-console-context.xml")
public class UReportConfiguration {

    @Bean
    public ServletRegistrationBean<UReportServlet> ureportServlet() {
        ServletRegistrationBean<UReportServlet> registration =
                new ServletRegistrationBean<>(new UReportServlet(), "/ureport/*");
        registration.setName("ureportServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
