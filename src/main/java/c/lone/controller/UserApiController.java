package c.lone.controller;

import c.lone.config.auth.CustomUserDetails;
import c.lone.service.AuthService;
import c.lone.service.FindService;
import c.lone.service.UserService;
import c.lone.utils.UserInfoSessionUpdate;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@AllArgsConstructor
public class UserApiController {
    private final UserService userService;
    private final AuthService authService;
    private final BCryptPasswordEncoder encodePwd;
    private final FindService findService;
    // private final FindServide findServide;

    @PatchMapping("/api/user/info")
    public ResponseEntity<String> modifyInfo(String value, String valueType, String prevPassword, @AuthenticationPrincipal CustomUserDetails principal, HttpSession session) {
        // value = 변경할 값
        // valueType = password, nickname, phone
        String username = principal.getUsername();
        String msg = "";

        switch (valueType) {
            /*
                비밀번호를 변경하고자 할 경우 BCryptPasswordEncoder의 matches 메서드를 통해 현재 비밀번호와 입력한 비밀번호 일치 확인 후
                일치할 경우 변경하고자 하는 비밀번호를 encode 메서드를 통해 암호화시키고 DB에 업데이트 해줌
                그 이후 스프링시큐리티 세션의 정보를 업데이트 해줌
             */
            case "password":
                if (!encodePwd.matches(prevPassword, principal.getPassword())) {
                    return ResponseEntity.badRequest().body("비밀번호가 일치하지 않습니다");
                }
                value = encodePwd.encode(value);
                msg = "비밀번호가 변경되었습니다";
                break;
            case "nickname":
                msg = "닉네임이 변경되었습니다";
                break;
            case "phone":
                /*
                    타이머를 사용하는 이유는 인증번호를 전송할 경우 그 인증번호를 세션에 저장해야하고,
                    사용자가 인증번호 입력시 세션에서 인증번호를 꺼내어 서로 비교해야 하는데
                    시간을 정해두지 않으면 세션이 계속해서 쌓이고 보안적인 측면에서 좋지 않음
                 */
                msg = "전화번호가 변경되었습니다";
                session.setMaxInactiveInterval(0);
                session.setAttribute("authNum", null);
                break;
        }
        userService.modifyInfo(username, valueType, value);
        UserInfoSessionUpdate.sessionUpdate(value, valueType, principal, session);
        return ResponseEntity.ok().body(msg);
    }

    // 리뷰 삭제
    @DeleteMapping("/api/user/review")
    public ResponseEntity<String> deleteReview(@AuthenticationPrincipal CustomUserDetails principal, String orderNum) {
        int id = principal.getId();
        userService.deleteReview(id, orderNum);
        return ResponseEntity.ok().body("리뷰 삭제 완료");
    }

    // 인증번호 보내기
    @PostMapping("/api/user/sendAuthNum")
    private ResponseEntity<String> authNum(String phone, String email, HttpSession session) {
        String authNum = "";
        for (int i = 0; i < 6; i++) {
            authNum += (int) (Math.random() * 10);
        }
        System.out.println("인증번호 : " + authNum);

        // 전화번호로 인증번호 보내기 추가
        if (phone != null) {
            System.out.println("전화번호로 인증번호 보내기");
        } else if (email != null) {
            findService.sendAuthNum(email, authNum);
        }
        Map<String, Object> authNumMap = new HashMap<>();
        long createTime = System.currentTimeMillis();//인증번호 생성시간
        long endTime = createTime * (300 * 1000); //인증번호 만료시간

        authNumMap.put("createTime", createTime);
        authNumMap.put("endTime", endTime);
        authNumMap.put("authNum", authNum);

        //세션 타임아웃 설정
        session.setMaxInactiveInterval(300);
        session.setAttribute("authNum", authNumMap);

        return new ResponseEntity<String>("인증번호가 전송되었습니다.", HttpStatus.OK);
    }

    //인증번호가 맞는지 확인
    @PostMapping("/api/user/authNumCheck")
    private ResponseEntity<String> authNumCheck(String authNum, HttpSession session) {
        Map<String, Object> sessionAuthNumMap = (Map<String, Object>) session.getAttribute("authNum");
        String msg = "";

        if (sessionAuthNumMap == null) {
            msg = "인증번호를 전송해주세요";
            return new ResponseEntity<String>(msg, HttpStatus.BAD_REQUEST);
        }

        //인증번호 만료시간
        long endTime = (long) sessionAuthNumMap.get("endTime");

        //현재시간이 만료 시간이 지났다면
        if (System.currentTimeMillis() > endTime) {
            msg = "인증시간이 만료되었습니다";
            session.setAttribute(authNum, null);
            session.setMaxInactiveInterval(0);
            return new ResponseEntity<String>(msg, HttpStatus.BAD_REQUEST);
        }

        //인증번호
        String sessionAuthNum = (String) sessionAuthNumMap.get("authNum");
        if (!authNum.equals(sessionAuthNum)) {
            msg = "인증번호가 일치하지 않습니다";
            return new ResponseEntity<String>(msg, HttpStatus.BAD_REQUEST);
        } else {
            /*
                패스워드, 닉네임 변경의 경우
                사용자 입력 > AJAX 요청 > modifyInfo(회원정보수정) > DB업데이트
                핸드폰 번호 변경의 경우
                사용자 입력 > 타이머 실행 > AJAX 요청 > authNum(인증번호 전송) > AJAX 요청
                > authNumCheck(인증번호 확인) > AJAX 요청 > modifyInfo(회원정보수정) > DB 업데이트
             */

            return new ResponseEntity<String>(HttpStatus.OK);
        }


    }

    //비밀번호 변경
    @PatchMapping("/api/user/password")
    public ResponseEntity<String> modifyPassword(String password, String username, HttpSession session) {
        password = encodePwd.encode(password);
        userService.modifyInfo(username, "password", password);
        session.setMaxInactiveInterval(0);
        session.setAttribute("authStatus", null);
        return new ResponseEntity<String>("비밀번호를 변경했습니다", HttpStatus.OK);
    }

}

















































