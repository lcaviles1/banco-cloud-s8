package cl.duoc.bank.movement;

import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class LegacyMovementRepository {
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    private static final Set<String> VALID_MOVEMENT_TYPES = Set.of("deposito", "retiro", "compra");
    private static final Set<String> VALID_TRANSACTION_TYPES = Set.of("debito", "credito");

    private final Map<Long, List<Movement>> movements = new HashMap<>();
    private int movementRows;
    private int rejectedMovementRows;
    private int duplicateMovementRows;
    private int transactionRows;
    private int validTransactionRows;

    @PostConstruct
    void load() throws Exception {
        loadMovements();
        loadTransactionsQuality();
        movements.values().forEach(list -> list.sort(Comparator.comparing(Movement::fecha).reversed()));
    }

    private void loadMovements() throws Exception {
        Set<String> seen = new HashSet<>();
        try (var reader = reader("legacy/cuentas_anuales.csv");
             var parser = csv().parse(reader)) {
            for (CSVRecord row : parser) {
                movementRows++;
                Optional<Movement> parsed = parseMovement(row);
                if (parsed.isEmpty()) {
                    rejectedMovementRows++;
                    continue;
                }
                Movement movement = parsed.get();
                String fingerprint = movement.cuentaId() + "|" + movement.fecha() + "|"
                        + movement.tipo() + "|" + movement.monto() + "|" + movement.descripcion();
                if (!seen.add(fingerprint)) {
                    duplicateMovementRows++;
                    continue;
                }
                movements.computeIfAbsent(movement.cuentaId(), ignored -> new ArrayList<>()).add(movement);
            }
        }
    }

    private void loadTransactionsQuality() throws Exception {
        try (var reader = reader("legacy/transacciones.csv");
             var parser = csv().parse(reader)) {
            for (CSVRecord row : parser) {
                transactionRows++;
                try {
                    parseDate(row.get("fecha"));
                    new BigDecimal(row.get("monto"));
                    if (VALID_TRANSACTION_TYPES.contains(row.get("tipo").toLowerCase(Locale.ROOT))) {
                        validTransactionRows++;
                    }
                } catch (RuntimeException ignored) {
                    // La fila queda contabilizada como inválida en el reporte de calidad.
                }
            }
        }
    }

    private InputStreamReader reader(String path) throws Exception {
        return new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
    }

    private CSVFormat csv() {
        return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get();
    }

    private Optional<Movement> parseMovement(CSVRecord row) {
        try {
            long accountId = Long.parseLong(row.get("cuenta_id"));
            LocalDate date = parseDate(row.get("fecha"));
            String type = row.get("transaccion").trim().toLowerCase(Locale.ROOT);
            String rawAmount = row.get("monto").trim();
            if (!VALID_MOVEMENT_TYPES.contains(type) || rawAmount.isBlank()) {
                return Optional.empty();
            }
            BigDecimal absoluteAmount = new BigDecimal(rawAmount).abs();
            if (absoluteAmount.signum() == 0) {
                return Optional.empty();
            }
            BigDecimal signedAmount = type.equals("deposito") ? absoluteAmount : absoluteAmount.negate();
            String description = row.get("descripcion").trim();
            if (description.isBlank()) {
                description = "Sin descripción informada";
            }
            return Optional.of(new Movement(accountId, date, type, signedAmount, description, "CLP"));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private LocalDate parseDate(String value) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value.trim(), format);
            } catch (DateTimeParseException ignored) {
                // Se intenta el siguiente formato legacy conocido.
            }
        }
        throw new DateTimeParseException("Formato de fecha no soportado", value, 0);
    }

    public Overview overview(long accountId, int limit) {
        List<Movement> all = movements.getOrDefault(accountId, List.of());
        List<Movement> recent = all.stream().limit(limit).toList();
        BigDecimal credits = all.stream().map(Movement::monto).filter(v -> v.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal debits = all.stream().map(Movement::monto).filter(v -> v.signum() < 0)
                .map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate lastDate = all.isEmpty() ? null : all.getFirst().fecha();
        return new Overview(recent, new Summary(all.size(), credits, debits, lastDate));
    }

    public DataQuality quality() {
        return new DataQuality(movementRows, movementRows - rejectedMovementRows - duplicateMovementRows,
                rejectedMovementRows, duplicateMovementRows, transactionRows, validTransactionRows,
                transactionRows - validTransactionRows);
    }

    public record Overview(List<Movement> movimientos, Summary resumen) {}
    public record Summary(int cantidad, BigDecimal totalAbonos, BigDecimal totalCargos, LocalDate ultimoMovimiento) {}
    public record DataQuality(int filasMovimientos, int movimientosValidos, int movimientosRechazados,
                              int movimientosDuplicados, int filasTransacciones, int transaccionesValidas,
                              int transaccionesInvalidas) {}
}
