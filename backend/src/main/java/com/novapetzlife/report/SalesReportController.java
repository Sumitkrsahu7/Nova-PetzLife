package com.novapetzlife.report;

import com.novapetzlife.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reports/sales")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'STORE_MANAGER', 'STAFF_ADMIN') or hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_STAFF_ADMIN', 'REPORT_VIEW')")
@Tag(name = "Admin Sales Reports", description = "Management sales reports, KPI summaries, and CSV/PDF financial export downloads")
public class SalesReportController {

    private final SalesReportService salesReportService;

    public SalesReportController(SalesReportService salesReportService) {
        this.salesReportService = salesReportService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get sales report summary and orders", description = "Generates financial summary KPIs and detailed order rows for Weekly, Monthly, Yearly, or Custom period")
    public ResponseEntity<ApiResponse<SalesReportSummaryDto>> getSalesReport(
            @RequestParam(defaultValue = "WEEKLY") String period,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        SalesReportSummaryDto summary = salesReportService.generateSalesReport(period, date, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/export/csv")
    @Operation(summary = "Export sales report as CSV", description = "Generates and streams a downloadable CSV spreadsheet containing order-level transactions")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(defaultValue = "WEEKLY") String period,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        SalesReportSummaryDto summary = salesReportService.generateSalesReport(period, date, startDate, endDate);
        byte[] csvBytes = salesReportService.generateCsvReport(summary);

        String filename = "SalesReport-" + summary.period() + "-" + summary.queryDate() + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

        return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/export/pdf")
    @Operation(summary = "Export sales report as PDF", description = "Generates and streams a high-resolution management PDF report with KPI cards and transaction breakdown")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(defaultValue = "WEEKLY") String period,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        SalesReportSummaryDto summary = salesReportService.generateSalesReport(period, date, startDate, endDate);
        byte[] pdfBytes = salesReportService.generatePdfReport(summary);

        String filename = "SalesReport-" + summary.period() + "-" + summary.queryDate() + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}
