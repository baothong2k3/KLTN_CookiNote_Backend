/*
 * @ (#) ExcelExportService.java    1.0    27/10/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.services;/*
 * @description:
 * @author: Bao Thong
 * @date: 27/10/2025
 * @version: 1.0
 */

import fit.kltn_cookinote_backend.dtos.request.ExportRequest;

import java.io.IOException;

/**
 * Interface cho dịch vụ xuất file Excel.
 */
public interface ExcelExportService {
    /**
     * Xuất toàn bộ công thức ra file Excel và lưu vào đường dẫn được chỉ định hoặc mặc định.
     *
     * @param request Chứa đường dẫn lưu file tùy chọn.
     * @return Đường dẫn tuyệt đối của file Excel đã được lưu trên server.
     * @throws IOException
     */
    String exportAllRecipesMergedToExcelFile(ExportRequest request) throws IOException;
}
