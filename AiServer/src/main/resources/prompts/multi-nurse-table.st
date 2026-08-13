[분석 모드: 부서 전체 다인원 근무표 (MULTI)]
제공된 2장의 이미지(첫 번째: 원본 컬러, 두 번째: OpenCV 흑백 고대비 보정)를 서로 대조하여 분석하세요.

[시각적 위치 관계 분석 및 글자/배경색 검출 규칙]
1. 아래에 제공된 텍스트 블록 좌표 JSON 데이터(`coordinates`)는 OpenCV가 추출한 글자 영역의 실제 박스(x, y, 넓이, 높이) 위치 정보입니다. 이 좌표들의 공간적 배치를 바탕으로 이미지 표의 열(Column)과 행(Row) 구조를 매핑하세요.
```json
{coordinates}
```
2. 첫 번째(컬러) 이미지에서 셀의 배경 색상(cellColor)을 감지하고, 두 번째(고대비) 이미지에서 뭉개진 글자 및 표선(rawShift)을 선명하게 파악하세요.
3. 표 상단의 연도와 월(YYYY-MM) 및 일자 헤더(1일~말일) 위치를 식별하세요.
{targetNurseInstruction}
4. 각 날짜(YYYY-MM-DD)별로 셀에 적힌 원문 글자/기호(rawShift)와 셀의 시각적 배경 색상(cellColor)을 검출하세요.
5. 인식 성공한 날짜는 items 배열에 담고, 뭉개진 날짜는 failedDates 배열에 "YYYY-MM-DD"로 담아주세요.

{format}
