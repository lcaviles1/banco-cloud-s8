package cl.bancoxyz.batchservice;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class BatchPolicyTest {
 @Test void monthlyRatesAndRounding(){assertEquals(new BigDecimal("12.50"),BatchPolicy.monthly(new BigDecimal("5000"),new BigDecimal("0.03")));assertEquals(new BigDecimal("80.00"),BatchPolicy.monthly(new BigDecimal("8000"),new BigDecimal("0.12")));assertEquals(new BigDecimal("46.67"),BatchPolicy.monthly(new BigDecimal("7000"),new BigDecimal("0.08")));}
 @Test void impossibleDatesNeverNormalize(){for(String s:new String[]{"2024-13-01","31/02/2024","2024-02-30"})assertThrows(IllegalArgumentException.class,()->BatchPolicy.date(s));assertEquals("2024-02-29",BatchPolicy.date("29/02/2024").toString());}
 @Test void alternateFormats(){for(String s:new String[]{"2024-01-04","2024/01/04","04/01/2024","04-01-2024"})assertEquals("2024-01-04",BatchPolicy.date(s).toString());}
 @Test void invalidAmountNotSilentlyCorrected(){for(String s:new String[]{"","-100","0","1.001"})assertThrows(IllegalArgumentException.class,()->BatchPolicy.amount(s,false));assertEquals(BigDecimal.ZERO,BatchPolicy.amount("0",true));}
 @Test void annualDirectionDoesNotDoubleNegate(){assertEquals(new BigDecimal("-500"),BatchPolicy.annualSigned("retiro","-500"));assertEquals(new BigDecimal("-500"),BatchPolicy.annualSigned("retiro","500"));assertThrows(IllegalArgumentException.class,()->BatchPolicy.annualSigned("deposito","-500"));}
 @Test void accentNormalization(){assertEquals("deposito",BatchPolicy.type(" Depósito "));}
}
