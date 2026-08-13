package com.likeLion.backend.aiserver.service.layer;

import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.List;

@Component
public class OpenCvImagePreprocessor {

    private static final Logger log = LoggerFactory.getLogger(OpenCvImagePreprocessor.class);

    public record PreprocessedResult(byte[] processedBytes, String coordinatesJson) {}

    @PostConstruct
    public void init() {
        try {
            OpenCV.loadLocally();
            log.info("OpenCV 네이티브 라이브러리가 성공적으로 로드되었습니다.");
        } catch (Exception e) {
            log.error("OpenCV 라이브러리 로드 실패", e);
        }
    }

    /**
     * 원본 이미지 바이트 배열을 받아 OpenCV 흑백 고대비(Adaptive Threshold + CLAHE) 처리 및 좌표 추출 후 반환
     */
    public PreprocessedResult processToHighContrastGrayscale(byte[] originalImageBytes) {
        if (originalImageBytes == null || originalImageBytes.length == 0) {
            return new PreprocessedResult(originalImageBytes, "[]");
        }

        try {
            MatOfByte inputByteMat = new MatOfByte(originalImageBytes);
            Mat srcMat = Imgcodecs.imdecode(inputByteMat, Imgcodecs.IMREAD_COLOR);

            if (srcMat.empty()) {
                log.warn("OpenCV 디코딩 실패, 원본 이미지를 그대로 사용합니다.");
                return new PreprocessedResult(originalImageBytes, "[]");
            }

            Mat deskewedMat = deskew(srcMat);
            Mat bgRemovedMat = removePastelBackground(deskewedMat);

            Mat grayMat = new Mat();
            Imgproc.cvtColor(bgRemovedMat, grayMat, Imgproc.COLOR_BGR2GRAY);

            Mat contrastMat = new Mat();
            Imgproc.createCLAHE(2.0, new Size(8, 8)).apply(grayMat, contrastMat);

            Mat highContrastMat = new Mat();
            Imgproc.adaptiveThreshold(
                    contrastMat,
                    highContrastMat,
                    255,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY,
                    15,
                    4
            );

            // 좌표 추출 로직
            String coordinatesJson = extractCellCoordinates(highContrastMat);

            MatOfByte outputByteMat = new MatOfByte();
            Imgcodecs.imencode(".jpg", highContrastMat, outputByteMat);
            byte[] processedBytes = outputByteMat.toArray();

            srcMat.release();
            deskewedMat.release();
            bgRemovedMat.release();
            grayMat.release();
            contrastMat.release();
            highContrastMat.release();
            inputByteMat.release();
            outputByteMat.release();

            return new PreprocessedResult(processedBytes, coordinatesJson);

        } catch (Exception e) {
            log.error("OpenCV 전처리 중 오류 발생, 원본 이미지를 반환합니다.", e);
            return new PreprocessedResult(originalImageBytes, "[]");
        }
    }

    /**
     * 모폴로지 연산으로 텍스트 및 셀 영역을 뭉쳐서 좌표 추출 후 JSON 반환
     */
    private String extractCellCoordinates(Mat highContrastGray) {
        Mat threshInv = new Mat();
        Core.bitwise_not(highContrastGray, threshInv);

        Mat morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(15, 5));
        Mat dilated = new Mat();
        Imgproc.dilate(threshInv, dilated, morphKernel);

        List<org.opencv.core.MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(dilated, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        contours.sort((c1, c2) -> {
            org.opencv.core.Rect r1 = Imgproc.boundingRect(c1);
            org.opencv.core.Rect r2 = Imgproc.boundingRect(c2);
            if (Math.abs(r1.y - r2.y) > 15) {
                return Integer.compare(r1.y, r2.y);
            }
            return Integer.compare(r1.x, r2.x);
        });

        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("[\n");
        int boxIndex = 1;
        for (org.opencv.core.MatOfPoint contour : contours) {
            org.opencv.core.Rect rect = Imgproc.boundingRect(contour);
            if (rect.width > 10 && rect.height > 10 && rect.area() > 100 && rect.area() < (highContrastGray.rows() * highContrastGray.cols() * 0.5)) {
                if (boxIndex > 1) {
                    jsonBuilder.append(",\n");
                }
                jsonBuilder.append(String.format("  {\"id\": %d, \"x\": %d, \"y\": %d, \"w\": %d, \"h\": %d}", 
                        boxIndex, rect.x, rect.y, rect.width, rect.height));
                boxIndex++;
            }
        }
        jsonBuilder.append("\n]");

        threshInv.release();
        morphKernel.release();
        dilated.release();
        hierarchy.release();

        log.info("좌표 추출 완료: 총 {} 개의 Bounding Box", boxIndex - 1);
        return jsonBuilder.toString();
    }

    /**
     * 허프 변환을 이용한 이미지 텍스트/표 기울기 보정 (Deskew)
     */
    private Mat deskew(Mat src) {
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        
        // Canny 에지 검출 대신 Threshold 기반으로 선분 검출 최적화
        Core.bitwise_not(gray, gray);
        Mat thresh = new Mat();
        Imgproc.threshold(gray, thresh, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

        Mat lines = new Mat();
        Imgproc.HoughLinesP(thresh, lines, 1, Math.PI / 180, 100, 100, 10);

        double angle = 0;
        int count = 0;

        for (int i = 0; i < lines.rows(); i++) {
            double[] val = lines.get(i, 0);
            double theta = Math.atan2(val[3] - val[1], val[2] - val[0]) * 180.0 / Math.PI;
            // 수평선에 가까운 선들만 각도 계산에 포함 (-45 ~ 45도)
            if (Math.abs(theta) < 45 && Math.abs(theta) > 0.1) {
                angle += theta;
                count++;
            }
        }

        gray.release();
        thresh.release();
        lines.release();

        if (count > 0) {
            angle /= count;
        }

        // 각도가 유의미하게 틀어졌을 때만 회전 보정
        if (Math.abs(angle) < 0.1 || Math.abs(angle) > 45) {
            return src.clone();
        }

        log.info("이미지 기울기 보정 수행: {} 도", angle);
        Point center = new Point(src.cols() / 2.0, src.rows() / 2.0);
        Mat rotMat = Imgproc.getRotationMatrix2D(center, angle, 1.0);
        Mat dst = new Mat();
        // 배경을 흰색(255,255,255)으로 채우면서 회전
        Imgproc.warpAffine(src, dst, rotMat, src.size(), Imgproc.INTER_CUBIC, Core.BORDER_CONSTANT, new Scalar(255, 255, 255));
        rotMat.release();

        return dst;
    }

    /**
     * HSV 색공간을 활용하여 파스텔톤 배경을 흰색으로 치환 (검은 텍스트 보존)
     */
    private Mat removePastelBackground(Mat src) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(src, hsv, Imgproc.COLOR_BGR2HSV);

        List<Mat> hsvChannels = new ArrayList<>();
        Core.split(hsv, hsvChannels);

        // V(Value/명도) 채널 추출 - 텍스트는 보통 어두움 (V값이 낮음)
        Mat vChannel = hsvChannels.get(2);

        // 텍스트가 아닌 영역 (배경) 검출. 명도가 어느정도 높은 영역을 배경으로 간주
        // 검정 텍스트 영역을 보호하기 위한 마스크
        Mat bgMask = new Mat();
        Imgproc.threshold(vChannel, bgMask, 150, 255, Imgproc.THRESH_BINARY);

        Mat result = src.clone();
        // 배경 마스크에 해당하는 영역(밝은 파스텔톤)을 순백색으로 변경
        result.setTo(new Scalar(255, 255, 255), bgMask);

        hsv.release();
        for (Mat channel : hsvChannels) {
            channel.release();
        }
        vChannel.release();
        bgMask.release();

        return result;
    }
}
