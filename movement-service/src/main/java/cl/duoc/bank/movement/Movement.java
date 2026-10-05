package cl.duoc.bank.movement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Movement(long cuentaId, LocalDate fecha, String tipo, BigDecimal monto,
                       String descripcion, String moneda) {
}
