package dev.tuiop.filedrop;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

public class FileDropModularityTests {


    private final ApplicationModules modules =
            ApplicationModules.of(FileDropApplication.class);



    @Test
    void verifiesModuleStructure(){
    modules.verify();
    }

}
