package cl.bancoxyz.batchservice;

import java.util.*;
import java.math.*;
import java.nio.file.*;
import java.io.*;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.*;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.*;
import org.springframework.batch.item.file.*;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.transform.*;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;
@Configuration
public class BatchConfiguration {
 public record Raw(long row,String[] fields){}
 public record Result(long row,String status,String reason,String entity,String date,String type,BigDecimal value,String description,String raw){}
 private final JobRepository repo;private final PlatformTransactionManager tm;private final JdbcTemplate db;private final Path data,runtime;private final int workers;
 public BatchConfiguration(JobRepository r,PlatformTransactionManager t,JdbcTemplate d,@Value("${bank.batch.data:/app/data}") String data,@Value("${bank.batch.runtime:/app/runtime}") String out,@Value("${bank.batch.workers:4}") int w){repo=r;tm=t;db=d;this.data=Path.of(data);runtime=Path.of(out);workers=w;if(w<1||w>16)throw new IllegalArgumentException("workers 1..16");}
 @Bean public ThreadPoolTaskExecutor partitionExecutor(){var e=new ThreadPoolTaskExecutor();e.setCorePoolSize(workers);e.setMaxPoolSize(workers);e.setQueueCapacity(32);e.setThreadNamePrefix("batch-partition-");return e;}
 @Bean @StepScope public FlatFileItemReader<Raw> reader(@Value("#{stepExecutionContext['file']}") String file,@Value("#{jobParameters['kind']}") String kind){String[] names=kind.equals("daily")?new String[]{"row","id","fecha","monto","tipo"}:kind.equals("interest")?new String[]{"row","cuenta_id","nombre","saldo","edad","tipo"}:new String[]{"row","cuenta_id","fecha","transaccion","monto","descripcion"};return new FlatFileItemReaderBuilder<Raw>().name("legacyReader").resource(new FileSystemResource(file)).delimited().names(names).fieldSetMapper(f->new Raw(f.readLong(0),java.util.Arrays.copyOfRange(f.getValues(),1,f.getFieldCount()))).saveState(true).build();}
 @Bean @StepScope public ItemProcessor<Raw,Result> processor(@Value("#{jobParameters['kind']}") String kind,@Value("#{jobParameters['runKey']}") String key,@Value("#{jobParameters['period']}") String period,@Value("#{jobParameters['savingsRate']}") String savings,@Value("#{jobParameters['loanRate']}") String loan,@Value("#{jobParameters['mortgageRate']}") String mortgage){return r->{String raw=String.join(",",r.fields());String entity=r.fields()[0].trim();try{
  if(Long.parseLong(entity)<=0)throw new IllegalArgumentException("INVALID_ID");
  String natural=kind.equals("annual")?digest(java.util.Arrays.stream(r.fields()).map(String::trim).collect(java.util.stream.Collectors.joining(","))):entity;
  Integer count=db.queryForObject("select occurrences from batch_keys where run_key=? and natural_key=?",Integer.class,key,natural);if(count>1)throw new IllegalArgumentException("DUPLICATE_KEY");
  if(kind.equals("interest")){String name=r.fields()[1].trim();if(name.isBlank())throw new IllegalArgumentException("EMPTY_NAME");BigDecimal balance=BatchPolicy.amount(r.fields()[2],true);int age=Integer.parseInt(r.fields()[3].trim());if(age<18||age>100)throw new IllegalArgumentException("INVALID_AGE");String type=BatchPolicy.type(r.fields()[4]);BigDecimal rate=switch(type){case "ahorro"->new BigDecimal(savings);case "prestamo"->new BigDecimal(loan);case "hipoteca"->new BigDecimal(mortgage);default->throw new IllegalArgumentException("INVALID_TYPE");};return new Result(r.row(),"VALID",null,entity,period,type,BatchPolicy.monthly(balance,rate),name,raw);}
  var date=BatchPolicy.date(r.fields()[1]);if(date.getYear()!=Integer.parseInt(period))throw new IllegalArgumentException("OUTSIDE_PERIOD");
  if(kind.equals("daily")){var amount=BatchPolicy.amount(r.fields()[2],false);String type=BatchPolicy.type(r.fields()[3]);if(!Set.of("credito","debito").contains(type))throw new IllegalArgumentException("INVALID_TYPE");return new Result(r.row(),"VALID",null,entity,date.toString(),type,amount,amount.compareTo(new BigDecimal("3000"))>=0?"HIGH_VALUE":"",raw);}
  String type=BatchPolicy.type(r.fields()[2]);var value=BatchPolicy.annualSigned(type,r.fields()[3]);String desc=r.fields()[4].trim();if(desc.isBlank())throw new IllegalArgumentException("EMPTY_DESCRIPTION");return new Result(r.row(),"VALID",null,entity,date.toString(),type,value,desc,raw);
 }catch(IllegalArgumentException e){return new Result(r.row(),"REJECTED",e instanceof NumberFormatException?"INVALID_NUMBER":e.getMessage(),entity,"","",BigDecimal.ZERO,"",raw);}};}
 @Bean @StepScope public ItemWriter<Result> writer(@Value("#{jobParameters['runKey']}") String key){return chunk->{for(Result r:chunk){db.update("insert into batch_records(run_key,row_no,status,reason,entity,value_date,type,value,description,raw) values(?,?,?,?,?,?,?,?,?,?) on conflict(run_key,row_no) do update set status=excluded.status,reason=excluded.reason,entity=excluded.entity,value_date=excluded.value_date,type=excluded.type,value=excluded.value,description=excluded.description,raw=excluded.raw",key,r.row(),r.status(),r.reason(),r.entity(),r.date(),r.type(),r.value(),r.description(),r.raw());}};}
 @Bean @StepScope public Partitioner partitioner(@Value("#{jobParameters['runKey']}") String key){return size->{var out=new LinkedHashMap<String,ExecutionContext>();for(int i=0;i<workers;i++){var c=new ExecutionContext();c.putString("file",runtime.resolve(key).resolve("part-"+i+".csv").toString());out.put("p"+i,c);}return out;};}
 @Bean public Step worker(FlatFileItemReader<Raw> reader,ItemProcessor<Raw,Result> processor,ItemWriter<Result> writer){var backoff=new ExponentialBackOffPolicy();backoff.setInitialInterval(200);backoff.setMultiplier(2);backoff.setMaxInterval(2000);return new StepBuilder("validate-transform-write",repo).<Raw,Result>chunk(100,tm).reader(reader).processor(processor).writer(writer).faultTolerant().retry(TransientDataAccessException.class).retryLimit(3).backOffPolicy(backoff).build();}
 @Bean public Step partitions(Step worker,Partitioner partitioner,ThreadPoolTaskExecutor partitionExecutor){return new StepBuilder("parallel-legacy",repo).partitioner("validate-transform-write",partitioner).step(worker).gridSize(workers).taskExecutor(partitionExecutor).build();}
 private Step prepare(String kind){return new StepBuilder("prepare-"+kind,repo).tasklet((contribution,context)->{
  var p=contribution.getStepExecution().getJobParameters();String key=p.getString("runKey"),week=p.getString("week");Path source=data.resolve(week).resolve(filename(kind));if(!digest(Files.readString(source)).equals(p.getString("inputHash")))throw new IllegalStateException("Entrada modificada; restaurar CSV original");
  Path dir=runtime.resolve(key);Files.createDirectories(dir);db.update("delete from batch_keys where run_key=?",key);db.update("delete from batch_records where run_key=?",key);
  List<BufferedWriter> outs=new ArrayList<>();try{for(int i=0;i<workers;i++)outs.add(Files.newBufferedWriter(dir.resolve("part-"+i+".csv")));try(var lines=Files.newBufferedReader(source)){lines.readLine();String line;long row=0;while((line=lines.readLine())!=null){row++;String natural=kind.equals("annual")?digest(java.util.Arrays.stream(line.split(",",-1)).map(String::trim).collect(java.util.stream.Collectors.joining(","))):line.split(",",-1)[0].trim();db.update("insert into batch_keys values(?,?,1) on conflict(run_key,natural_key) do update set occurrences=batch_keys.occurrences+1",key,natural);var w=outs.get((int)((row-1)%workers));w.write(row+","+line);w.newLine();}}}finally{for(var w:outs)w.close();}return org.springframework.batch.repeat.RepeatStatus.FINISHED;
 },tm).build();}
 private Step export(String kind){return new StepBuilder("export-"+kind,repo).tasklet((contribution,context)->{String key=contribution.getStepExecution().getJobParameters().getString("runKey");Path dir=runtime.resolve(key);String sql=switch(kind){case "daily"->"select value_date as fecha,type,count(*) as operaciones,sum(value) as monto,sum(case when value>=3000 then 1 else 0 end) as alertas from batch_records where run_key=? and status='VALID' group by value_date,type order by value_date,type";case "interest"->"select entity as cuenta,value_date as mes,type,value as interes,description as titular from batch_records where run_key=? and status='VALID' order by cast(entity as bigint)";default->"select entity as cuenta,count(*) as movimientos,sum(case when value>0 then value else 0 end) as depositos,sum(case when value<0 then -value else 0 end) as egresos,sum(value) as movimiento_neto from batch_records where run_key=? and status='VALID' group by entity order by cast(entity as bigint)";};
  csv(dir.resolve("resultado.csv"),db.queryForList(sql,key),kind.equals("daily")?new String[]{"fecha","type","operaciones","monto","alertas"}:kind.equals("interest")?new String[]{"cuenta","mes","type","interes","titular"}:new String[]{"cuenta","movimientos","depositos","egresos","movimiento_neto"});csv(dir.resolve("rechazados.csv"),db.queryForList("select row_no as fila,reason as motivo,raw as original from batch_records where run_key=? and status='REJECTED' order by row_no",key),"fila","motivo","original");csv(dir.resolve("detalle.csv"),db.queryForList("select row_no,entity,value_date,type,value,description from batch_records where run_key=? and status='VALID' order by row_no",key),"row_no","entity","value_date","type","value","description");
  return org.springframework.batch.repeat.RepeatStatus.FINISHED;},tm).build();}
 @Bean public Job dailyJob(Step partitions){return new JobBuilder("dailyTransactions",repo).start(prepare("daily")).next(partitions).next(export("daily")).build();}
 @Bean public Job interestJob(Step partitions){return new JobBuilder("monthlyInterest",repo).start(prepare("interest")).next(partitions).next(export("interest")).build();}
 @Bean public Job annualJob(Step partitions){return new JobBuilder("annualStatements",repo).start(prepare("annual")).next(partitions).next(export("annual")).build();}
 public static String filename(String kind){return switch(kind){case "daily"->"movimientos_financieros_diarios.csv";case "interest"->"intereses_trimestrales.csv";case "annual"->"estados_financieros_anuales.csv";default->throw new IllegalArgumentException("Job desconocido");};}
 public static String digest(String s){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private void csv(Path path,List<Map<String,Object>> records,String... headers)throws IOException{
  Path tmp=path.resolveSibling(path.getFileName()+".tmp");
  try(var w=Files.newBufferedWriter(tmp)){
   w.write(String.join(",",records.isEmpty()?java.util.Arrays.asList(headers):records.getFirst().keySet()));w.newLine();
   for(var r:records){w.write(r.values().stream().map(x->"\""+String.valueOf(x==null?"":x).replace("\"","\"\"")+"\"").collect(java.util.stream.Collectors.joining(",")));w.newLine();}
  }
  Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);
 }
}
