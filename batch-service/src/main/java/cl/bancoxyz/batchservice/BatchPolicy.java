package cl.bancoxyz.batchservice;

import java.math.*;
import java.time.*;
import java.time.format.*;
import java.text.Normalizer;
import java.util.*;
public final class BatchPolicy {
 public static String type(String t){return Normalizer.normalize(t.trim().toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","");}
 public static LocalDate date(String text){for(String f:List.of("uuuu-MM-dd","uuuu/MM/dd","dd/MM/uuuu","dd-MM-uuuu")){try{return LocalDate.parse(text.trim(),DateTimeFormatter.ofPattern(f).withResolverStyle(ResolverStyle.STRICT));}catch(DateTimeParseException ignored){}}throw new IllegalArgumentException("INVALID_DATE");}
 public static BigDecimal amount(String text,boolean zero){try{var v=new BigDecimal(text.trim());if(v.scale()>2||v.precision()>16||v.signum()<0||(!zero&&v.signum()==0))throw new IllegalArgumentException("INVALID_AMOUNT");return v;}catch(NumberFormatException e){throw new IllegalArgumentException("MISSING_OR_INVALID_AMOUNT");}}
 public static BigDecimal monthly(BigDecimal balance,BigDecimal annualRate){if(annualRate.signum()<0||annualRate.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("INVALID_RATE");return balance.multiply(annualRate).divide(new BigDecimal("12"),2,RoundingMode.HALF_UP);}
 public static BigDecimal annualSigned(String t,String raw){t=type(t);if(!Set.of("deposito","retiro","compra","pago").contains(t))throw new IllegalArgumentException("INVALID_TYPE");try{BigDecimal m=new BigDecimal(raw.trim());if(t.equals("deposito"))return amount(raw,false);return amount(m.abs().toPlainString(),false).negate();}catch(NumberFormatException e){throw new IllegalArgumentException("MISSING_OR_INVALID_AMOUNT");}}
}
