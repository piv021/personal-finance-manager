package ru.promo.finance.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Утилита для красивого табличного вывода в консоль с границами.
 */
public class TableFormatter {

    public static String renderTable(List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty()) return "";

        int cols = headers.size();
        int[] colWidths = new int[cols];

        for (int i = 0; i < cols; i++) {
            colWidths[i] = Math.max(colWidths[i], visibleLength(headers.get(i)));
        }

        if (rows != null) {
            for (List<String> row : rows) {
                for (int i = 0; i < Math.min(cols, row.size()); i++) {
                    colWidths[i] = Math.max(colWidths[i], visibleLength(row.get(i)));
                }
            }
        }

        // Add 2 padding chars
        for (int i = 0; i < cols; i++) {
            colWidths[i] += 2;
        }

        StringBuilder sb = new StringBuilder();
        // Top border
        sb.append(buildSeparator("┌", "┬", "┐", colWidths)).append("\n");

        // Header row
        sb.append("│");
        for (int i = 0; i < cols; i++) {
            sb.append(center(headers.get(i), colWidths[i])).append("│");
        }
        sb.append("\n");

        // Header separator
        sb.append(buildSeparator("├", "┼", "┤", colWidths)).append("\n");

        // Rows
        if (rows != null && !rows.isEmpty()) {
            for (List<String> row : rows) {
                sb.append("│");
                for (int i = 0; i < cols; i++) {
                    String val = (i < row.size() && row.get(i) != null) ? row.get(i) : "";
                    sb.append(padRight(val, colWidths[i])).append("│");
                }
                sb.append("\n");
            }
        } else {
            sb.append("│").append(center("(нет данных)", sum(colWidths) + cols - 1)).append("│\n");
        }

        // Bottom border
        sb.append(buildSeparator("└", "┴", "┘", colWidths));

        return sb.toString();
    }

    private static String buildSeparator(String left, String mid, String right, int[] widths) {
        StringBuilder sb = new StringBuilder(left);
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i]));
            if (i < widths.length - 1) {
                sb.append(mid);
            }
        }
        sb.append(right);
        return sb.toString();
    }

    private static String padRight(String s, int width) {
        int vLen = visibleLength(s);
        int pad = width - vLen - 1;
        if (pad < 0) pad = 0;
        return " " + s + " ".repeat(pad);
    }

    private static String center(String s, int width) {
        int vLen = visibleLength(s);
        int pad = width - vLen;
        if (pad <= 0) return s;
        int left = pad / 2;
        int right = pad - left;
        return " ".repeat(left) + s + " ".repeat(right);
    }

    private static int visibleLength(String s) {
        if (s == null) return 0;
        return s.length();
    }

    private static int sum(int[] arr) {
        int s = 0;
        for (int v : arr) s += v;
        return s;
    }
}
