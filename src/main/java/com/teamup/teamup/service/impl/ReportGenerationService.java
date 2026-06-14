package com.teamup.teamup.service.impl;

import com.teamup.teamup.dto.GroupReportDto;
import com.teamup.teamup.dto.GroupReportDto.MemberReportRow;
import com.teamup.teamup.dto.GroupReportDto.SubmissionLogRow;
import com.teamup.teamup.entity.Group;
import com.teamup.teamup.entity.PeerReview;
import com.teamup.teamup.entity.Submission;
import com.teamup.teamup.entity.Task;
import com.teamup.teamup.entity.User;
import com.teamup.teamup.exception.ResourceNotFoundException;
import com.teamup.teamup.repository.*;
import com.teamup.teamup.service.dto.MemberContributionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates the Group Report as an Excel (.xlsx) file.
 *
 * <h3>Document structure</h3>
 * <pre>
 * Sheet 1 — "Summary"
 *   ├─ Header block  (Group name, subject, semester, generated date)
 *   ├─ Summary stats (total members, avg attitude, late submissions)
 *   └─ Member table (position | name | contribution% | attitude score | attitude label | submissions | late | on-time%)
 *
 * Sheet 2 — "Submission Log"
 *   ├─ Header block
 *   └─ Chronological table (task | submitter | submitted at | deadline | status | file | size)
 * </pre>
 *
 * <h3>Data aggregation (three sources)</h3>
 * <ol>
 *   <li><strong>Contribution %</strong> — from {@code ProgressCalculationService.calculateAllContributionsInGroup}</li>
 *   <li><strong>ON_TIME / LATE</strong> — computed by comparing {@code submission.submittedAt} vs {@code task.deadline}</li>
 *   <li><strong>Attitude score</strong> — from {@code PeerReviewRepository.averageScoreForMember}</li>
 * </ol>
 *
 * <h3>Style guide</h3>
 * - Header cells: dark blue (#1F3864), white bold text, centered
 * - Sub-header: medium blue (#2F5496), white text, bold
 * - Alternate row shading: light blue (#D9E1F2) / white
 * - LATE status: red bold text
 * - ON_TIME status: green bold text
 * - Summary box: light grey (#F2F2F2) background
 * - All borders: thin
 *
 * <h3>Thread-safety</h3>
 * POI Workbook is NOT thread-safe. This service must be {@code @Scope("prototype")}
 * or instantiated fresh per call. The controller creates a new service instance per request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReportGenerationService {

    private final GroupRepository          groupRepository;
    private final SubmissionRepository     submissionRepository;
    private final PeerReviewRepository    peerReviewRepository;
    private final ProgressCalculationService progressService;
    private final UserRepository          userRepository;

    private static final String FONT_NAME = "Calibri";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ══════════════════════════════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Generates a complete group report as an Excel byte array.
     *
     * @param groupId  the group to generate the report for
     * @return byte array of a valid .xlsx file
     * @throws ResourceNotFoundException if the group does not exist
     */
    public byte[] generateExcelReport(Long groupId) {
        log.info("Generating Excel report for group {}", groupId);

        GroupReportDto report = buildReport(groupId);
        return buildExcelBytes(report);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Data aggregation — builds the GroupReportDto from three sources
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Aggregates contribution data + submission log + peer scores into a GroupReportDto.
     */
    public GroupReportDto buildReport(Long groupId) {
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));

        List<User> members = new ArrayList<>(group.getMembers());

        // ── 1. Contribution % per member ─────────────────────────────────────────
        Map<Long, MemberContributionDto> contributions =
            progressService.calculateAllContributionsInGroup(groupId);

        // ── 2. Submissions log ──────────────────────────────────────────────────
        List<Object[]> submissionRows = submissionRepository.findAllFilesInGroup(groupId);
        List<SubmissionLogRow> log = buildSubmissionLog(submissionRows);

        // ── 3. Peer attitude scores ─────────────────────────────────────────────
        Map<Long, Double> avgScores = new HashMap<>();
        for (User member : members) {
            Double avg = peerReviewRepository.averageScoreForMember(groupId, member.getId());
            avgScores.put(member.getId(), avg != null ? Math.round(avg * 10.0) / 10.0 : null);
        }

        // ── 4. Build member rows ────────────────────────────────────────────────
        Map<Long, Long> memberSubmissionCount = log.stream()
            .collect(Collectors.groupingBy(
                row -> findMemberIdByName(members, row.getSubmitterName()),
                Collectors.counting()));

        Map<Long, Long> lateCount = log.stream()
            .filter(row -> row.getStatus() == SubmissionLogRow.SubmissionStatus.LATE)
            .collect(Collectors.groupingBy(
                row -> findMemberIdByName(members, row.getSubmitterName()),
                Collectors.counting()));

        // Sort members by ID for consistent anonymous position numbering
        List<User> sortedMembers = members.stream()
            .sorted(Comparator.comparing(User::getId))
            .toList();

        Map<Long, Integer> positionMap = new HashMap<>();
        for (int i = 0; i < sortedMembers.size(); i++) {
            positionMap.put(sortedMembers.get(i).getId(), i + 1);
        }

        List<MemberReportRow> memberRows = sortedMembers.stream().map(member -> {
            Long memberId = member.getId();
            int total = memberSubmissionCount.getOrDefault(memberId, 0L).intValue();
            int late  = lateCount.getOrDefault(memberId, 0L).intValue();
            double onTimePct = total > 0 ? Math.round((total - late) * 1000.0 / total) / 10.0 : 0.0;

            return MemberReportRow.builder()
                .memberPosition(positionMap.get(memberId))
                .memberName(member.getName())
                .contributionPercent(
                    contributions.containsKey(memberId)
                        ? Math.round(contributions.get(memberId).getContributionPercent() * 10.0) / 10.0
                        : 0.0)
                .averageAttitudeScore(avgScores.get(memberId))
                .attitudeLabel(labelForScore(avgScores.get(memberId)))
                .totalSubmissions(total)
                .lateSubmissions(late)
                .onTimePercent(onTimePct)
                .build();
        }).toList();

        // ── 5. Group-level stats ─────────────────────────────────────────────────
        double groupAvg = avgScores.values().stream()
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .average().orElse(0.0);

        int totalLate = (int) lateCount.values().stream().mapToLong(Long::longValue).sum();

        return GroupReportDto.builder()
            .groupId(groupId)
            .groupName(group.getGroupName())
            .subjectCode(group.getSubjectCode())
            .subjectName(group.getSubjectName())
            .semester(group.getClassEntity().getSemester())
            .members(memberRows)
            .groupAverageAttitudeScore(Math.round(groupAvg * 10.0) / 10.0)
            .inactiveMemberCount((int) memberRows.stream().filter(r -> r.getTotalSubmissions() == 0).count())
            .totalLateSubmissions(totalLate)
            .submissionLog(log)
            .build();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Excel generation — Apache POI
    // ══════════════════════════════════════════════════════════════════════════════

    private byte[] buildExcelBytes(GroupReportDto report) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Create and style the workbook
            XSSFCellStyle headerStyle = buildHeaderStyle(wb);
            XSSFCellStyle subHeaderStyle = buildSubHeaderStyle(wb);
            XSSFCellStyle altRowStyle   = buildAltRowStyle(wb);
            XSSFCellStyle normalStyle   = buildNormalStyle(wb);
            XSSFCellStyle lateStyle     = buildLateStyle(wb);
            XSSFCellStyle onTimeStyle   = buildOnTimeStyle(wb);
            XSSFCellStyle summaryStyle  = buildSummaryStyle(wb);
            XSSFCellStyle statValueStyle = buildStatValueStyle(wb);

            // ── Sheet 1: Summary ────────────────────────────────────────────────
            XSSFSheet summarySheet = wb.createSheet("Summary");
            summarySheet.setDefaultColumnWidth(28);
            writeSummarySheet(summarySheet, report, headerStyle, subHeaderStyle,
                altRowStyle, normalStyle, lateStyle, onTimeStyle, summaryStyle, statValueStyle);

            // ── Sheet 2: Submission Log ──────────────────────────────────────────
            XSSFSheet logSheet = wb.createSheet("Submission Log");
            logSheet.setDefaultColumnWidth(24);
            writeLogSheet(logSheet, report, headerStyle, subHeaderStyle,
                altRowStyle, normalStyle, lateStyle, onTimeStyle);

            wb.write(out);
            log.info("Excel report generated: {} bytes", out.size());
            return out.toByteArray();

        } catch (IOException ex) {
            throw new RuntimeException("Failed to generate Excel report", ex);
        }
    }

    private void writeSummarySheet(XSSFSheet sheet, GroupReportDto report,
            XSSFCellStyle header, XSSFCellStyle subHeader,
            XSSFCellStyle altRow, XSSFCellStyle normal,
            XSSFCellStyle late, XSSFCellStyle onTime,
            XSSFCellStyle summary, XSSFCellStyle statValue) {

        int row = 0;

        // ── Title block ───────────────────────────────────────────────────────
        row = writeTitleBlock(sheet, report, header, row);

        // ── Summary stats box ─────────────────────────────────────────────────
        row = writeSummaryStats(sheet, report, summary, statValue, header, subHeader, normal, row);

        // ── Member table ──────────────────────────────────────────────────────
        row += 1;
        writeMemberTableHeader(sheet, subHeader, row);
        row++;

        boolean shade = false;
        for (MemberReportRow member : report.getMembers()) {
            XSSFCellStyle rowStyle = shade ? altRow : normal;
            shade = !shade;

            XSSFRow r = sheet.createRow(row++);
            setCell(r, 0, member.getMemberPosition(),      rowStyle, HorizontalAlignment.CENTER);
            setCell(r, 1, member.getMemberName(),          rowStyle, HorizontalAlignment.LEFT);
            setCell(r, 2, formatPct(member.getContributionPercent()), rowStyle, HorizontalAlignment.CENTER);
            setCell(r, 3, member.getAverageAttitudeScore() != null
                ? String.format("%.1f / 5.0", member.getAverageAttitudeScore()) : "N/A", rowStyle, HorizontalAlignment.CENTER);
            setCell(r, 4, member.getAttitudeLabel(),       rowStyle, HorizontalAlignment.CENTER);
            setCell(r, 5, member.getTotalSubmissions(),    rowStyle, HorizontalAlignment.CENTER);
            setCell(r, 6, member.getLateSubmissions(),     rowStyle, HorizontalAlignment.CENTER);
            setCell(r, 7, formatPct(member.getOnTimePercent()), rowStyle, HorizontalAlignment.CENTER);
        }

        // Auto-size columns
        for (int i = 0; i < 8; i++) {
            sheet.autoSizeColumn(i);
        }

        // Freeze header row
        sheet.createFreezePane(0, row - report.getMembers().size());
    }

    private void writeLogSheet(XSSFSheet sheet, GroupReportDto report,
            XSSFCellStyle header, XSSFCellStyle subHeader,
            XSSFCellStyle altRow, XSSFCellStyle normal,
            XSSFCellStyle late, XSSFCellStyle onTime) {

        int row = 0;

        // ── Title block ───────────────────────────────────────────────────────
        row = writeTitleBlock(sheet, report, header, row);

        // ── Table header ─────────────────────────────────────────────────────
        String[] logHeaders = {"Task", "Submitter", "Submitted At", "Deadline", "Status", "File Name", "Size"};
        XSSFRow hRow = sheet.createRow(row++);
        for (int i = 0; i < logHeaders.length; i++) {
            setCell(hRow, i, logHeaders[i], subHeader, HorizontalAlignment.CENTER);
        }

        // ── Data rows ────────────────────────────────────────────────────────
        boolean shade = false;
        for (SubmissionLogRow entry : report.getSubmissionLog()) {
            XSSFCellStyle rowStyle = shade ? altRow : normal;
            shade = !shade;

            XSSFRow r = sheet.createRow(row++);
            setCell(r, 0, entry.getTaskName(),       rowStyle, HorizontalAlignment.LEFT);
            setCell(r, 1, entry.getSubmitterName(),  rowStyle, HorizontalAlignment.LEFT);
            setCell(r, 2, entry.getSubmittedAt(),    rowStyle, HorizontalAlignment.CENTER);
            setCell(r, 3, entry.getTaskDeadline(),   rowStyle, HorizontalAlignment.CENTER);

            XSSFCell statusCell = r.createCell(4);
            statusCell.setCellValue(entry.getStatus().name().replace("_", " "));
            if (entry.getStatus() == SubmissionLogRow.SubmissionStatus.LATE) {
                statusCell.setCellStyle(late);
            } else if (entry.getStatus() == SubmissionLogRow.SubmissionStatus.ON_TIME) {
                statusCell.setCellStyle(onTime);
            } else {
                statusCell.setCellStyle(normal);
            }

            setCell(r, 5, entry.getFileName(),      rowStyle, HorizontalAlignment.LEFT);
            setCell(r, 6, entry.getFileSizeHuman(), rowStyle, HorizontalAlignment.CENTER);
        }

        // Auto-size
        for (int i = 0; i < 7; i++) sheet.autoSizeColumn(i);
        sheet.createFreezePane(0, row - report.getSubmissionLog().size());
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Title block (shared between both sheets)
    // ══════════════════════════════════════════════════════════════════════════════

    private int writeTitleBlock(XSSFSheet sheet, GroupReportDto report,
            XSSFCellStyle headerStyle, int startRow) {

        int row = startRow;

        // Main title
        XSSFRow titleRow = sheet.createRow(row++);
        XSSFCell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("TEAMUP GROUP REPORT");
        titleCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(startRow, startRow, 0, 5));

        // Group name
        XSSFRow gRow = sheet.createRow(row++);
        setCell(gRow, 0, "Group:",        headerStyle, HorizontalAlignment.RIGHT);
        setCell(gRow, 1, report.getGroupName(), headerStyle, HorizontalAlignment.LEFT);

        // Subject
        XSSFRow sRow = sheet.createRow(row++);
        setCell(sRow, 0, "Subject:",      headerStyle, HorizontalAlignment.RIGHT);
        setCell(sRow, 1, String.format("%s — %s", report.getSubjectCode(), report.getSubjectName()),
            headerStyle, HorizontalAlignment.LEFT);

        // Semester
        if (report.getSemester() != null) {
            XSSFRow semRow = sheet.createRow(row++);
            setCell(semRow, 0, "Semester:",   headerStyle, HorizontalAlignment.RIGHT);
            setCell(semRow, 1, report.getSemester(), headerStyle, HorizontalAlignment.LEFT);
        }

        // Generated date
        XSSFRow dateRow = sheet.createRow(row++);
        setCell(dateRow, 0, "Generated:",   headerStyle, HorizontalAlignment.RIGHT);
        setCell(dateRow, 1, LocalDateTime.now().format(TS), headerStyle, HorizontalAlignment.LEFT);

        return row; // next row index after title block
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Summary stats block
    // ══════════════════════════════════════════════════════════════════════════════

    private int writeSummaryStats(XSSFSheet sheet, GroupReportDto report,
            XSSFCellStyle boxStyle, XSSFCellStyle valueStyle,
            XSSFCellStyle labelStyle, XSSFCellStyle subHeader,
            XSSFCellStyle normal, int startRow) {

        int row = startRow;

        // Box header
        XSSFRow hRow = sheet.createRow(row++);
        XSSFCell hCell = hRow.createCell(0);
        hCell.setCellValue("GROUP SUMMARY");
        hCell.setCellStyle(boxStyle);
        sheet.addMergedRegion(new CellRangeAddress(startRow, startRow, 0, 3));

        // Stats
        XSSFRow mRow = sheet.createRow(row++);
        setCell(mRow, 0, "Total Members:",     labelStyle, HorizontalAlignment.RIGHT);
        setCell(mRow, 1, report.getMembers().size(), valueStyle, HorizontalAlignment.CENTER);

        setCell(mRow, 2, "Avg Attitude Score:", labelStyle, HorizontalAlignment.RIGHT);
        String avgScore = report.getGroupAverageAttitudeScore() > 0
            ? String.format("%.1f / 5.0", report.getGroupAverageAttitudeScore())
            : "N/A";
        setCell(mRow, 3, avgScore, valueStyle, HorizontalAlignment.CENTER);

        XSSFRow lRow = sheet.createRow(row++);
        setCell(lRow, 0, "Inactive Members:",   labelStyle, HorizontalAlignment.RIGHT);
        setCell(lRow, 1, report.getInactiveMemberCount(), valueStyle, HorizontalAlignment.CENTER);

        setCell(lRow, 2, "Late Submissions:",  labelStyle, HorizontalAlignment.RIGHT);
        setCell(lRow, 3, report.getTotalLateSubmissions(), valueStyle, HorizontalAlignment.CENTER);

        return row;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Member table header
    // ══════════════════════════════════════════════════════════════════════════════

    private void writeMemberTableHeader(XSSFSheet sheet, XSSFCellStyle style, int rowIdx) {
        String[] cols = {
            "#", "Name", "Contribution %", "Attitude Score",
            "Attitude Label", "Submissions", "Late", "On-Time %"
        };
        XSSFRow hRow = sheet.createRow(rowIdx);
        for (int i = 0; i < cols.length; i++) {
            setCell(hRow, i, cols[i], style, HorizontalAlignment.CENTER);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Cell helpers
    // ══════════════════════════════════════════════════════════════════════════════

    private void setCell(XSSFRow row, int col, Object value,
                         CellStyle style, HorizontalAlignment align) {
        Cell cell = row.createCell(col);
        if (value instanceof Number num) {
            cell.setCellValue(num.doubleValue());
        } else {
            cell.setCellValue(value != null ? value.toString() : "");
        }
        cell.setCellStyle(style);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // POI style builders
    // ══════════════════════════════════════════════════════════════════════════════

    private XSSFCellStyle buildHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName(FONT_NAME);
        font.setFontHeightInPoints((short) 16);
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(font);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0x1F, (byte)0x38, (byte)0x64}));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private XSSFCellStyle buildSubHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName(FONT_NAME);
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(font);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0x2F, (byte)0x54, (byte)0x96}));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private XSSFCellStyle buildAltRowStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName(FONT_NAME);
        font.setFontHeightInPoints((short) 11);
        s.setFont(font);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xD9, (byte)0xE1, (byte)0xF2}));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private XSSFCellStyle buildNormalStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName(FONT_NAME);
        font.setFontHeightInPoints((short) 11);
        s.setFont(font);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private XSSFCellStyle buildLateStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName(FONT_NAME);
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        font.setColor(IndexedColors.RED.getIndex());
        s.setFont(font);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private XSSFCellStyle buildOnTimeStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName(FONT_NAME);
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        font.setColor(IndexedColors.GREEN.getIndex());
        s.setFont(font);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private XSSFCellStyle buildSummaryStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName(FONT_NAME);
        font.setFontHeightInPoints((short) 12);
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(font);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0x1F, (byte)0x38, (byte)0x64}));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private XSSFCellStyle buildStatValueStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName(FONT_NAME);
        font.setFontHeightInPoints((short) 12);
        font.setBold(true);
        font.setColor(new XSSFColor(new byte[]{(byte)0x1F, (byte)0x38, (byte)0x64}));
        s.setFont(font);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ══════════════════════════════════════════════════════════════════════════════

    private List<SubmissionLogRow> buildSubmissionLog(List<Object[]> rows) {
        return rows.stream().map(row -> {
            Submission s   = (Submission) row[0];
            String     tn  = (String)    row[1];
            String     un  = (String)    row[2];

            LocalDateTime deadline    = s.getTask().getDeadline();
            LocalDateTime submittedAt = s.getSubmittedAt();

            SubmissionLogRow.SubmissionStatus status;
            if (deadline == null) {
                status = SubmissionLogRow.SubmissionStatus.NO_DEADLINE;
            } else if (submittedAt.isAfter(deadline)) {
                status = SubmissionLogRow.SubmissionStatus.LATE;
            } else {
                status = SubmissionLogRow.SubmissionStatus.ON_TIME;
            }

            return SubmissionLogRow.builder()
                .taskName(tn)
                .submitterName(un)
                .submittedAt(submittedAt.format(TS))
                .taskDeadline(deadline != null ? deadline.format(DT) : "—")
                .status(status)
                .fileName(s.getFileName())
                .fileSizeHuman(FileManagementService.formatFileSize(s.getFileSize()))
                .build();
        }).toList();
    }

    private Long findMemberIdByName(List<User> members, String name) {
        return members.stream()
            .filter(m -> m.getName().equals(name))
            .findFirst()
            .map(User::getId)
            .orElse(-1L);
    }

    private String labelForScore(Double score) {
        if (score == null) return "No Reviews";
        return switch ((int) Math.round(score)) {
            case 1 -> "Very Poor";
            case 2 -> "Poor";
            case 3 -> "Satisfactory";
            case 4 -> "Good";
            case 5 -> "Excellent";
            default -> "No Reviews";
        };
    }

    private String formatPct(Double value) {
        if (value == null) return "—";
        return String.format("%.1f%%", value);
    }
}
