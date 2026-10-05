package cl.duoc.bank.account;

import java.math.BigDecimal;
import java.time.Instant;

public record Account(
        long cuentaId,
        String titular,
        BigDecimal saldo,
        String moneda,
        int edad,
        String tipo,
        String estado,
        Instant actualizadoEn) {
}
