package cl.duoc.bank.account;

import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class LegacyAccountRepository {
    private static final Set<String> VALID_TYPES = Set.of("ahorro", "prestamo", "hipoteca");
    private final Map<Long, MutableAccount> accounts = new LinkedHashMap<>();
    private int totalRows;
    private int rejectedRows;
    private int duplicateRows;

    @PostConstruct
    void load() throws Exception {
        var resource = new ClassPathResource("legacy/intereses.csv");
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             var parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .get()
                     .parse(reader)) {
            for (CSVRecord row : parser) {
                totalRows++;
                Optional<MutableAccount> parsed = parse(row);
                if (parsed.isEmpty()) {
                    rejectedRows++;
                    continue;
                }
                MutableAccount account = parsed.get();
                if (accounts.putIfAbsent(account.id, account) != null) {
                    duplicateRows++;
                }
            }
        }
    }

    private Optional<MutableAccount> parse(CSVRecord row) {
        try {
            long id = Long.parseLong(row.get("cuenta_id"));
            String name = row.get("nombre").trim();
            String rawBalance = row.get("saldo").trim();
            String rawAge = row.get("edad").trim();
            String type = row.get("tipo").trim().toLowerCase(Locale.ROOT);
            if (name.isBlank() || name.equalsIgnoreCase("unknown") || rawBalance.isBlank()
                    || rawAge.isBlank() || !VALID_TYPES.contains(type)) {
                return Optional.empty();
            }
            BigDecimal balance = new BigDecimal(rawBalance);
            int age = Integer.parseInt(rawAge);
            if (balance.signum() < 0 || age < 18 || age > 100) {
                return Optional.empty();
            }
            return Optional.of(new MutableAccount(id, name, balance, age, type));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public synchronized Optional<Account> find(long id) {
        return Optional.ofNullable(accounts.get(id)).map(MutableAccount::snapshot);
    }

    public synchronized Account withdraw(long id, BigDecimal amount) {
        MutableAccount account = Optional.ofNullable(accounts.get(id))
                .orElseThrow(() -> new AccountNotFoundException(id));
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidWithdrawalException("El monto debe ser mayor que cero");
        }
        if (account.balance.compareTo(amount) < 0) {
            throw new InvalidWithdrawalException("Saldo insuficiente");
        }
        account.balance = account.balance.subtract(amount);
        account.updatedAt = Instant.now();
        return account.snapshot();
    }

    public synchronized java.util.List<Account> all() { return accounts.values().stream().map(MutableAccount::snapshot).toList(); }

    public DataQuality quality() {
        return new DataQuality(totalRows, accounts.size(), rejectedRows, duplicateRows,
                "Los registros inválidos se omiten y los duplicados conservan la primera versión válida");
    }

    public record DataQuality(int filasLeidas, int cuentasDisponibles, int filasRechazadas,
                              int duplicadosOmitidos, String politica) {
    }

    private static final class MutableAccount {
        private final long id;
        private final String name;
        private BigDecimal balance;
        private final int age;
        private final String type;
        private Instant updatedAt = Instant.now();

        private MutableAccount(long id, String name, BigDecimal balance, int age, String type) {
            this.id = id;
            this.name = name;
            this.balance = balance;
            this.age = age;
            this.type = type;
        }

        private Account snapshot() {
            return new Account(id, name, balance, "CLP", age, type, "ACTIVA", updatedAt);
        }
    }
}

class AccountNotFoundException extends RuntimeException {
    AccountNotFoundException(long id) { super("No existe la cuenta " + id); }
}

class InvalidWithdrawalException extends RuntimeException {
    InvalidWithdrawalException(String message) { super(message); }
}
