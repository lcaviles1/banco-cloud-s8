package cl.duoc.bank.account;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyAccountRepositoryTest {
    @Test
    void loadsCleansAndUpdatesLegacyAccounts() throws Exception {
        LegacyAccountRepository repository = new LegacyAccountRepository();
        repository.load();

        assertThat(repository.quality().filasLeidas()).isEqualTo(1000);
        assertThat(repository.quality().cuentasDisponibles()).isPositive();
        assertThat(repository.quality().filasRechazadas()).isPositive();
        assertThat(repository.quality().duplicadosOmitidos()).isPositive();

        Account before = repository.find(101).orElseThrow();
        Account after = repository.withdraw(101, BigDecimal.valueOf(100));
        assertThat(after.saldo()).isEqualByComparingTo(before.saldo().subtract(BigDecimal.valueOf(100)));
    }

    @Test
    void rejectsWithdrawalAboveBalance() throws Exception {
        LegacyAccountRepository repository = new LegacyAccountRepository();
        repository.load();
        assertThatThrownBy(() -> repository.withdraw(101, new BigDecimal("999999999")))
                .isInstanceOf(InvalidWithdrawalException.class)
                .hasMessage("Saldo insuficiente");
    }
}
