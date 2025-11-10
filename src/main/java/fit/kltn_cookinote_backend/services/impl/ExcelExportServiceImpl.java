/*
 * @ (#) ExcelExportServiceImpl.java    1.0    27/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services.impl;/*
 * @description:
 * @author: Bao Thong
 * @date: 27/10/2025
 * @version: 1.0
 */

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import fit.kltn_cookinote_backend.dtos.request.ExportRequest;
import fit.kltn_cookinote_backend.entities.*;
import fit.kltn_cookinote_backend.repositories.RecipeRepository;
import fit.kltn_cookinote_backend.services.ExcelExportService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExcelExportServiceImpl implements ExcelExportService {

    private final RecipeRepository recipeRepository;
    private final Cloudinary cloudinary;

    @Value("${app.cloudinary.export-folder}")
    private String exportFolder;

    @Override
    @Transactional(readOnly = true)
    public String exportAllRecipesMergedToExcelFile(ExportRequest request) throws IOException {
        List<Recipe> recipes = recipeRepository.findAllWithUserAndCategory();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = "Recipes_Export_" + timestamp; // Tên file (không có .xlsx)

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream byteOut = new ByteArrayOutputStream()) {

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle wrapStyle = workbook.createCellStyle();
            wrapStyle.setWrapText(true); // Cho phép xuống dòng
            wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);
            wrapStyle.setBorderTop(BorderStyle.THIN);
            wrapStyle.setBorderBottom(BorderStyle.THIN);
            wrapStyle.setBorderLeft(BorderStyle.THIN);
            wrapStyle.setBorderRight(BorderStyle.THIN);

            CellStyle defaultStyle = workbook.createCellStyle();
            defaultStyle.setBorderTop(BorderStyle.THIN);
            defaultStyle.setBorderBottom(BorderStyle.THIN);
            defaultStyle.setBorderLeft(BorderStyle.THIN);
            defaultStyle.setBorderRight(BorderStyle.THIN);
            defaultStyle.setVerticalAlignment(VerticalAlignment.TOP);


            Sheet sheet = workbook.createSheet("All Recipes Data");

            // Định nghĩa headers cho sheet gộp
            String[] headers = {
                    "Recipe ID", "Title", "Description", "Prepare Time", "Cook Time", "Difficulty", "Privacy", "Image URL", "Views", "Created At", "Owner", "Category", // Recipe Info
                    "Ingredients (Name: Quantity)", // Ingredients
                    "Steps (No. Content [Time - Tips])" // Steps
            };
            createHeaderRow(sheet, headerStyle, headers);

            int rowNum = 1; // Bắt đầu từ dòng thứ 2 sau header

            for (Recipe recipe : recipes) {
                // ... (Giữ nguyên toàn bộ logic điền dữ liệu vào Row) ...
                Row row = sheet.createRow(rowNum++);

                // Ghi thông tin cơ bản của Recipe
                createCell(row, 0, recipe.getId(), defaultStyle);
                createCell(row, 1, recipe.getTitle(), defaultStyle);
                createCell(row, 2, recipe.getDescription(), wrapStyle); // Wrap text cho description
                createCell(row, 3, recipe.getPrepareTime() != null ? recipe.getPrepareTime() + " min" : "", defaultStyle);
                createCell(row, 4, recipe.getCookTime() != null ? recipe.getCookTime() + " min" : "", defaultStyle);
                createCell(row, 5, recipe.getDifficulty(), defaultStyle);
                createCell(row, 6, recipe.getPrivacy(), defaultStyle);
                createCell(row, 7, recipe.getImageUrl(), defaultStyle);
                createCell(row, 8, recipe.getView(), defaultStyle);
                createCell(row, 9, recipe.getCreatedAt() != null ? recipe.getCreatedAt().toString() : "", defaultStyle);
                createCell(row, 10, Optional.ofNullable(recipe.getUser()).map(User::getDisplayName).orElse("N/A"), defaultStyle);
                createCell(row, 11, Optional.ofNullable(recipe.getCategory()).map(Category::getName).orElse("N/A"), defaultStyle);

                // Gộp thông tin Ingredients vào một cell, mỗi nguyên liệu một dòng
                String ingredientsText = recipe.getIngredients().stream()
                        .map(i -> "- " + i.getName() + (StringUtils.hasText(i.getQuantity()) ? ": " + i.getQuantity() : ""))
                        .collect(Collectors.joining("\n")); // Xuống dòng
                createCell(row, 12, ingredientsText, wrapStyle);

                // Gộp thông tin Steps vào một cell, mỗi bước một dòng
                // Sắp xếp steps trước khi gộp
                recipe.getSteps().sort(Comparator.comparing(RecipeStep::getStepNo, Comparator.nullsLast(Comparator.naturalOrder())));
                String stepsText = recipe.getSteps().stream()
                        .map(s -> {
                            String stepInfo = s.getStepNo() + ". " + s.getContent();
                            String timeAndTips = "";
                            if (s.getSuggestedTime() != null && StringUtils.hasText(s.getTips())) {
                                timeAndTips = " [" + s.getSuggestedTime() + " min - " + s.getTips() + "]";
                            } else if (s.getSuggestedTime() != null) {
                                timeAndTips = " [" + s.getSuggestedTime() + " min]";
                            } else if (StringUtils.hasText(s.getTips())) {
                                timeAndTips = " [" + s.getTips() + "]";
                            }
                            return stepInfo + timeAndTips;
                        })
                        .collect(Collectors.joining("\n")); // Xuống dòng
                createCell(row, 13, stepsText, wrapStyle);
            }

            sheet.setColumnWidth(2, 80 * 256); // Cột Description rộng hơn
            sheet.setColumnWidth(12, 60 * 256); // Cột Ingredients rộng hơn
            sheet.setColumnWidth(13, 100 * 256); // Cột Steps rộng hơn
            for (int i = 0; i < headers.length; i++) {
                if (i != 2 && i != 12 && i != 13) { // Bỏ qua các cột đã set width cố định
                    sheet.autoSizeColumn(i);
                }
            }
            // Ghi workbook vào ByteArrayOutputStream thay vì file
            workbook.write(byteOut);

            // --- LOGIC UPLOAD LÊN CLOUDINARY ---
            byte[] excelData = byteOut.toByteArray();
            String publicId = exportFolder + "/" + fileName; // Public ID bao gồm cả thư mục

            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    excelData,
                    ObjectUtils.asMap(
                            "resource_type", "raw", // Quan trọng: dùng "raw" cho file không phải ảnh/video
                            "public_id", publicId,
                            "overwrite", true, // Cho phép ghi đè nếu trùng tên
                            "unique_filename", false,
                            "format", "xlsx" // Chỉ định định dạng file
                    )
            );

            String url = (String) uploadResult.get("secure_url");
            if (!StringUtils.hasText(url)) {
                throw new IOException("Upload file Excel lên Cloudinary thất bại.");
            }
            // Trả về URL của Cloudinary
            return url;

        }
    }

    // Tạo hàng header với style
    private void createHeaderRow(Sheet sheet, CellStyle style, String[] headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    // Overload createCell để nhận CellStyle
    private void createCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Long) {
            cell.setCellValue((Long) value);
        } else if (value instanceof Integer) {
            cell.setCellValue((Integer) value);
        } else if (value != null) {
            cell.setCellValue(value.toString());
        }
        cell.setCellStyle(style); // Áp dụng style
    }
}