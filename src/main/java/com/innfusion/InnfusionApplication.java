package com.innfusion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

@SpringBootApplication
public class InnfusionApplication {

	public static void main(String[] args) {
		SpringApplication.run(InnfusionApplication.class, args);
	}

	@Bean
	public MessageSource messageSource(){
		ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
		source.setBasename("classpath:/bundles/application_message");
		return source;
	}

}
