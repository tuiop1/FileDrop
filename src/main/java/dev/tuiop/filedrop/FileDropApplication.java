package dev.tuiop.filedrop;

import dev.tuiop.filedrop.scanning.internal.malwarescan.clamav.ClamAvProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties(ClamAvProperties.class)
@SpringBootApplication
public class FileDropApplication {

    static void main(String[] args) {
        SpringApplication.run(FileDropApplication.class, args);
    }

}
