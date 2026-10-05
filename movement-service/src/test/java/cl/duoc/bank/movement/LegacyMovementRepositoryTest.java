package cl.duoc.bank.movement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyMovementRepositoryTest {
    @Test
    void normalizesFormatsSignsAndQualityCounters() throws Exception {
        LegacyMovementRepository repository = new LegacyMovementRepository();
        repository.load();

        var quality = repository.quality();
        assertThat(quality.filasMovimientos()).isEqualTo(1000);
        assertThat(quality.filasTransacciones()).isEqualTo(1000);
        assertThat(quality.movimientosValidos()).isPositive();
        assertThat(quality.movimientosRechazados()).isPositive();
        assertThat(quality.transaccionesInvalidas()).isPositive();

        var overview = repository.overview(101, 3);
        assertThat(overview.movimientos()).hasSizeLessThanOrEqualTo(3);
        assertThat(overview.resumen().cantidad()).isPositive();
        assertThat(overview.movimientos()).allSatisfy(movement -> {
            if (movement.tipo().equals("deposito")) {
                assertThat(movement.monto()).isPositive();
            } else {
                assertThat(movement.monto()).isNegative();
            }
        });
    }
}
