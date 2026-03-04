package com.example.commons;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ComponentScan(basePackages = "com.example.commons")
public class CommonsAutoConfiguration {
	// This class is intentionally left blank. It serves as a marker for component scanning and auto-configuration.
}