package c.lone.utils;

import c.lone.dto.ReviewDto;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/*
    한 폴더 안에 모든 파일을 넣어버리면 파일들이 점점 쌓이면서 폴더 참조시 오래 걸림
    현재 날짜를 기준으로 폴더를 만들어 그 안에 파일을 저장하면 각각의 폴더를 참조할 때 시간을 최소화할 수 있음.

    LocalDate.now()와 pattern을 통해 현재 날짜를 기준으로 폴더 이름 생성
    uploadFolder에는 업로드 파일이 저장될 최상위주소인 upload에 imageUploadFolder 현재날짜 기준으로 리뷰이미지가 저장될 주소가 들어가게 됨
    paths.get을 사용하면 경로구분자를 넣어주지 않아도 경로로 만들어줌

    uploadFolder와 imageUploadFolder를 합쳐주면 주소가 만들어지고, 해당 주소를 가진 폴더가 존재하지 않으면 dir.mkdirs()를 통해 폴더를 생성
    랜덤값을 만들어내는 uuid를 통해서 랜덤값 + 파일이름으로 해당 파일을 저장

    파일I/O는 예외가 발생할 수 있으므로 try catch 문으로 감싸줘야 하며 예외 발생시에는 false를 반환하여 작업에 실패 알려줌
    파일 저장 완료 후에 Dto 안에 ReviewImg에 파일이 저장된 주소 저장

 */
@Component
public class FileUpload {
    public boolean uploadReviewImg(ReviewDto reviewDto) {

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String uploadFolder = Paths.get("Users", "david", "upload").toString();
        String imageUploadFolder = Paths.get("reviewImg", today).toString();
        String uploadPath = Paths.get(uploadFolder, imageUploadFolder).toString();

        File dir = new File(uploadPath);
        if (dir.exists() == false) {
            dir.mkdirs();
        }

        UUID uuid = UUID.randomUUID();
        String reviewImgName = uuid + "_" + reviewDto.getFile().getOriginalFilename();

        try {
            File target = new File(uploadPath, reviewImgName);
            reviewDto.getFile().transferTo(target);
        } catch (IOException e) {
            return false;
        }

        reviewDto.setReviewImg("upload\\" + imageUploadFolder + "\\" + reviewImgName);
        return true;

    }
}