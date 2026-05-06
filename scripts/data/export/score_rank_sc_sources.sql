-- 四川官方一分一段来源登记；图片需 OCR + 人工核验后再导入 data_score_rank。
DELETE FROM data_source_registry WHERE province_code='SC' AND data_type='score_rank' AND year=2025;
INSERT INTO data_source_registry (province_code, province_name, year, subject_type, data_type, source_name, source_page_url, source_url, parse_method, status, row_count, notes) VALUES ('SC', '四川', 2025, '历史类', 'score_rank', '四川省教育考试院', 'https://www.sceea.cn/Html/202506/Newsdetail_4334.html', 'https://www.sceea.cn/Upload/image/20250625/20250625183923_5099.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183923_6884.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183923_8309.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183923_9726.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183924_1441.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183924_3319.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183924_4558.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183924_5637.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183924_7246.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183924_8314.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183924_9730.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183925_1081.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183925_2694.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183925_4476.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183925_5893.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183925_6974.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183925_8686.jpg
https://www.sceea.cn/Upload/image/20250625/20250625183926_0811.jpg', 'html_image_ocr', 'manual_review', 0, '官方页面以图片发布一分一段表；需 OCR 后人工抽样核对，再写入 data_score_rank。 图片数：18');
INSERT INTO data_source_registry (province_code, province_name, year, subject_type, data_type, source_name, source_page_url, source_url, parse_method, status, row_count, notes) VALUES ('SC', '四川', 2025, '物理类', 'score_rank', '四川省教育考试院', 'https://www.sceea.cn/Html/202506/Newsdetail_4335.html', 'https://www.sceea.cn/Upload/image/20250625/20250625184111_2608.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184111_4725.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184111_6328.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184111_7773.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184111_9703.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184112_3180.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184112_5619.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184112_7044.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184112_8113.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184112_9858.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184113_1833.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184113_3434.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184113_4858.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184113_6818.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184113_8427.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184113_9541.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184114_1795.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184114_3382.jpg
https://www.sceea.cn/Upload/image/20250625/20250625184114_5421.jpg', 'html_image_ocr', 'manual_review', 0, '官方页面以图片发布一分一段表；需 OCR 后人工抽样核对，再写入 data_score_rank。 图片数：19');
