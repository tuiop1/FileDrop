package dev.tuiop.filedrop.drop.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FileDropProperties.class)
class FileDropConfiguration {
}
