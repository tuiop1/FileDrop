package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.drop.internal.exception.EmptyFileException;
import dev.tuiop.filedrop.drop.internal.exception.FileTooLargeException;
import dev.tuiop.filedrop.drop.internal.exception.InvalidFileNameException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class FileDropValidator {


    @Value("${application.file.max-size}")
    private final DataSize MAX_FILEDROP_SIZE;

    // validation before saving the file to temporary storage
    public void firstFileDropValidation(MultipartFile file){


        if(file.isEmpty()) {
            throw new EmptyFileException();
        }

        if(file.getSize() > MAX_FILEDROP_SIZE.toBytes()){
            throw new FileTooLargeException(MAX_FILEDROP_SIZE.toBytes());
        }

        String fileOriginalName = file.getOriginalFilename();

        if( fileOriginalName == null ||  fileOriginalName.length() > 255){

            throw new InvalidFileNameException();

        }





    }

}
