package com.likeLion.backend.aiserver.service;

import com.likeLion.backend.aiserver.dto.RawExtractionResponse;
import com.likeLion.backend.aiserver.dto.ScheduleOcrResponse;
import com.likeLion.backend.aiserver.dto.ScheduleType;
import com.likeLion.backend.aiserver.service.layer.MultiNurseTableExtractor;
import com.likeLion.backend.aiserver.service.layer.OpenCvImagePreprocessor;
import com.likeLion.backend.aiserver.service.layer.PersonalCalendarExtractor;
import com.likeLion.backend.aiserver.service.layer.ScheduleShiftNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class ScheduleOcrServiceImpl implements ScheduleOcrService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleOcrServiceImpl.class);

    private final OpenCvImagePreprocessor openCvImagePreprocessor;
    private final MultiNurseTableExtractor multiNurseTableExtractor;
    private final PersonalCalendarExtractor personalCalendarExtractor;
    private final ScheduleShiftNormalizer shiftNormalizer;

    public ScheduleOcrServiceImpl(OpenCvImagePreprocessor openCvImagePreprocessor,
                                  MultiNurseTableExtractor multiNurseTableExtractor,
                                  PersonalCalendarExtractor personalCalendarExtractor,
                                  ScheduleShiftNormalizer shiftNormalizer) {
        this.openCvImagePreprocessor = openCvImagePreprocessor;
        this.multiNurseTableExtractor = multiNurseTableExtractor;
        this.personalCalendarExtractor = personalCalendarExtractor;
        this.shiftNormalizer = shiftNormalizer;
    }

    @Override
    public ScheduleOcrResponse processScheduleOcr(MultipartFile file, String userName, ScheduleType scheduleType) {
        if (file == null || file.isEmpty()) {
            return ScheduleOcrResponse.failure("업로드된 이미지 파일이 없거나 비어 있습니다.");
        }

        ScheduleType targetType = (scheduleType != null) ? scheduleType : ScheduleType.MULTI;

        try {
            byte[] originalBytes = file.getBytes();
            String contentType = file.getContentType();
            MimeType mimeType = (contentType != null) ? MimeTypeUtils.parseMimeType(contentType) : MimeTypeUtils.IMAGE_JPEG;

            // 1. 원본 이미지 Media
            ByteArrayResource originalResource = new ByteArrayResource(originalBytes) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "schedule.jpg";
                }
            };
            Media originalMedia = new Media(mimeType, originalResource);

            // 2. OpenCV 흑백 고대비 보정 이미지 Media 및 좌표 추출
            OpenCvImagePreprocessor.PreprocessedResult preprocessedResult = openCvImagePreprocessor.processToHighContrastGrayscale(originalBytes);
            byte[] processedBytes = preprocessedResult.processedBytes();
            String coordinatesJson = preprocessedResult.coordinatesJson();

            ByteArrayResource processedResource = new ByteArrayResource(processedBytes) {
                @Override
                public String getFilename() {
                    return "processed_schedule.jpg";
                }
            };
            Media processedMedia = new Media(MimeTypeUtils.IMAGE_JPEG, processedResource);

            // 3. 테스트/디버깅용 upload 폴더 저장
            try {
                java.nio.file.Path uploadDir = java.nio.file.Paths.get("upload");
                if (!java.nio.file.Files.exists(uploadDir)) {
                    java.nio.file.Files.createDirectories(uploadDir);
                }
                String timestamp = String.valueOf(System.currentTimeMillis());
                java.nio.file.Files.write(uploadDir.resolve("original_" + timestamp + ".jpg"), originalBytes);
                java.nio.file.Files.write(uploadDir.resolve("processed_" + timestamp + ".jpg"), processedBytes);
                log.info("디버그용 이미지 저장 완료: upload/original_{}.jpg, upload/processed_{}.jpg", timestamp, timestamp);
            } catch (Exception e) {
                log.warn("디버그용 이미지 저장 중 실패 (OCR 프로세스는 계속 진행됩니다)", e);
            }

            log.info("요청된 근무표 레이아웃 서식: {}, 간호사명: {}, OpenCV 전처리 수행 완료", targetType, userName);

            // Layer 1: Dual-Image (원본 + 고대비) Vision 원문 검출 Layer
            RawExtractionResponse rawResponse;
            if (targetType == ScheduleType.PERSONAL) {
                log.info("개인 달력 Dual-Vision 검출기(PersonalCalendarExtractor) 실행");
                rawResponse = personalCalendarExtractor.extract(originalMedia, processedMedia, coordinatesJson);
            } else {
                log.info("부서 전체 근무표 Dual-Vision 검출기(MultiNurseTableExtractor) 실행");
                rawResponse = multiNurseTableExtractor.extract(originalMedia, processedMedia, userName, coordinatesJson);
            }

            if (rawResponse == null) {
                return ScheduleOcrResponse.failure("이미지 텍스트 검출에 실패했습니다.");
            }

            // Layer 2: Java 하드코딩 규칙 기반 정규화 Layer (Rule-based Normalizer)
            ScheduleOcrResponse finalResponse = shiftNormalizer.normalize(rawResponse);
            return finalResponse;

        } catch (IOException e) {
            log.error("파일 읽기 중 오류 발생", e);
            return ScheduleOcrResponse.failure("이미지 파일 처리 중 오류가 발생했습니다: " + e.getMessage());
        } catch (Exception e) {
            log.error("OCR 분석 중 오류 발생", e);
            return ScheduleOcrResponse.failure("OCR 이미지 분석 실패: " + e.getMessage());
        }
    }
}

