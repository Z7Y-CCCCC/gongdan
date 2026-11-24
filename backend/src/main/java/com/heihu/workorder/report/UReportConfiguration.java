package com.heihu.workorder.report;

import com.bstek.ureport.console.UReportServlet;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.PropertySource;

/**
 * Registers UReport2 inside the existing Spring Boot web application.
 * No second service or container is needed.
 */
@Configuration
@ImportResource("classpath:ureport-console-context.xml")
@PropertySource("classpath:ureport.properties")
public class UReportConfiguration {

    @Bean
    public static BeanDefinitionRegistryPostProcessor useSpringBootPropertyResolution() {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                // The vendor's global legacy resolver substitutes annotation defaults
                // before Boot can read the active profile and deployment environment.
                String legacy = "ureport.propertyPlaceholderConfigurer";
                if (registry.containsBeanDefinition(legacy)) registry.removeBeanDefinition(legacy);
            }

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {}
        };
    }

    @Bean
    public ServletRegistrationBean<UReportServlet> ureportServlet() {
        ServletRegistrationBean<UReportServlet> registration =
                new ServletRegistrationBean<>(new UReportServlet(), "/ureport/*");
        registration.setName("ureportServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
