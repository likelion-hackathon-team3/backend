package com.likeLion.backend.aiserver.exception;

import com.likeLion.backend.aiserver.dto.ScheduleOcrResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ScheduleOcrResponse> handleMissingServletRequestPart(MissingServletRequestPartException e) {
        log.warn("필수 요청 파라미터(파일) 누락: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ScheduleOcrResponse.failure("요청 파일('file')이 누락되었습니다."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ScheduleOcrResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("업로드 용량 초과: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ScheduleOcrResponse.failure("업로드 가능한 이미지 용량을 초과하였습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ScheduleOcrResponse> handleAllUncaughtException(Exception e) {
        log.error("서버 내부 예외 발생: ", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ScheduleOcrResponse.failure("서버 내부 오류가 발생했습니다: " + e.getMessage()));
    }
}
