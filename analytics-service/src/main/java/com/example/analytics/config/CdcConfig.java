package com.example.analytics.config;

import com.example.analytics.cdc.CdcProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds app.cdc.* (Postgres change data capture settings). */
@Configuration
@EnableConfigurationProperties(CdcProperties.class)
public class CdcConfig {
}
