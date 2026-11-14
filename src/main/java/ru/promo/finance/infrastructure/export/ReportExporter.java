package ru.promo.finance.infrastructure.export;

import ru.promo.finance.domain.model.FinancialSummary;
import ru.promo.finance.domain.model.Transaction;
import ru.promo.finance.domain.model.TransactionType;
import ru.promo.finance.domain.model.Wallet;
import ru.promo.finance.infrastructure.storage.FileStorageService;
import ru.promo.finance.infrastructure.storage.SimpleJson;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Сервис экспорта и импорта отчетов и данных (CSV, JSON, TXT).
 */
public class ReportExporter {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final FileStorageService storageService;

    public ReportExporter(FileStorageService storageService) {
        this.storageService = storageService != null ? storageService : new FileStorageService();
    }

    public ReportExporter() {
        this(new FileStorageService());
    }

    // ================= CSV EXPORT / IMPORT =================

    public void exportTransactionsToCsv(List<Transaction> transactions, Path target) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("id,type,category,amount,note,timestamp\n");
        for (Transaction t : transactions) {
            sb.append(escapeCsv(t.getId())).append(",")
                    .append(escapeCsv(t.getType().name())).append(",")
                    .append(escapeCsv(t.getCategory())).append(",")
                    .append(t.getAmount().toPlainString()).append(",")
                    .append(escapeCsv(t.getNote())).append(",")
                    .append(t.getTimestamp().format(ISO)).append("\n");
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
    }

    public int importTransactionsFromCsv(Path source, Wallet wallet) throws IOException {
        if (!Files.exists(source)) {
            throw new IllegalArgumentException("Файл не найден: " + source);
        }
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) return 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cols = parseCsvLine(line);
                if (cols.size() < 6) continue;
                String id = cols.get(0).trim();
                TransactionType type = TransactionType.valueOf(cols.get(1).trim());
                String category = cols.get(2).trim();
                BigDecimal amount = new BigDecimal(cols.get(3).trim());
                String note = cols.get(4).trim();
                LocalDateTime ts = LocalDateTime.parse(cols.get(5).trim(), ISO);

                // Избегаем дублирования по ID
                boolean exists = wallet.getTransactions().stream().anyMatch(t -> t.getId().equals(id));
                if (!exists) {
                    wallet.addTransaction(new Transaction(id, wallet.getUserId(), type, category, amount, note, ts));
                    count++;
                }
            }
        }
        return count;
    }

    // ================= JSON EXPORT / IMPORT =================

    public void exportWalletToJson(Wallet wallet, String userLogin, Path target) throws IOException {
        Map<String, Object> map = storageService.serializeWallet(wallet, userLogin);
        String json = SimpleJson.stringify(map);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, json, StandardCharsets.UTF_8);
    }

    public Wallet importWalletFromJson(Path source, String userId) throws IOException {
        if (!Files.exists(source)) {
            throw new IllegalArgumentException("Файл не найден: " + source);
        }
        String json = Files.readString(source, StandardCharsets.UTF_8);
        Map<String, Object> map = SimpleJson.parseObject(json);
        return storageService.deserializeWallet(map, userId);
    }

    // ================= TEXT REPORT EXPORT =================

    public void exportReportToText(String formattedReport, Path target) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, formattedReport, StandardCharsets.UTF_8);
    }

    // ================= CSV PARSER HELPERS =================

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private List<String> parseCsvLine(String line) {
        List<String> res = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                res.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        res.add(sb.toString());
        return res;
    }
}
