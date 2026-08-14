package dev.tuiop.filedrop.drop;

import dev.tuiop.filedrop.scanning.FileValidator;
import dev.tuiop.filedrop.storage.TemporaryFileStorage;
import org.mockito.Mock;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest
public class DropModuleIntegrationTests {

    @MockitoBean
    FileValidator fileValidator;

    @MockitoBean
    TemporaryFileStorage temporaryFileStorage;
}
