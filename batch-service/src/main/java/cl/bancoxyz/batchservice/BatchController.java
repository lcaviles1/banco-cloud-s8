package cl.bancoxyz.batchservice;

import java.util.*;
import java.nio.file.*;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.*;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/batch")
public class BatchController {
 private final Map<String,Job> jobs;private final JobLauncher launcher;private final JobExplorer explorer;private final JdbcTemplate db;private final Path data,out;private final int workers;
 public BatchController(@Qualifier("dailyJob") Job daily,@Qualifier("interestJob") Job interest,@Qualifier("annualJob") Job annual,@Qualifier("asyncLauncher") JobLauncher l,JobExplorer e,JdbcTemplate d,@Value("${bank.batch.data:/app/data}") String data,@Value("${bank.batch.runtime:/app/runtime}") String out,@Value("${bank.batch.workers:4}") int workers){jobs=Map.of("daily",daily,"interest",interest,"annual",annual);launcher=l;explorer=e;db=d;this.data=Path.of(data);this.out=Path.of(out);this.workers=workers;}
 @PostMapping("/{kind}") @PreAuthorize("hasAuthority('SCOPE_batch.run')") public ResponseEntity<?> start(@PathVariable String kind,@RequestParam(defaultValue="semana_1") String week,@RequestParam(defaultValue="2024") String period,@RequestParam(defaultValue="0.03") String savingsRate,@RequestParam(defaultValue="0.12") String loanRate,@RequestParam(defaultValue="0.08") String mortgageRate)throws Exception{
  if(!jobs.containsKey(kind)||!week.matches("semana_[123]")||!(kind.equals("interest")?period.matches("\\d{4}-(0[1-9]|1[0-2])"):period.matches("\\d{4}")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Job semana o periodo invalido; intereses requieren AAAA-MM");
  for(String rate:List.of(savingsRate,loanRate,mortgageRate))try{BatchPolicy.monthly(java.math.BigDecimal.ONE,new java.math.BigDecimal(rate));}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tasa invalida");}
  String hash=BatchConfiguration.digest(Files.readString(data.resolve(week).resolve(BatchConfiguration.filename(kind))));
  String key=kind+"-"+week+"-"+period+"-"+BatchConfiguration.digest(hash+savingsRate+loanRate+mortgageRate+workers+"v1").substring(0,12);
  var params=new JobParametersBuilder().addString("runKey",key).addString("policyVersion","v1").addLong("workers",(long)workers).addString("kind",kind).addString("week",week).addString("period",period).addString("inputHash",hash).addString("savingsRate",savingsRate).addString("loanRate",loanRate).addString("mortgageRate",mortgageRate).toJobParameters();
  try{var run=launcher.run(jobs.get(kind),params);db.update("insert into batch_runs(execution_id,run_key,retries) values(?,?,0) on conflict do nothing",run.getId(),key);return ResponseEntity.accepted().body(Map.of("executionId",run.getId(),"runKey",key));}catch(org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException|org.springframework.batch.core.repository.JobExecutionAlreadyRunningException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"Instancia completada o en ejecucion");}
 }
 @GetMapping("/runs") @PreAuthorize("hasAuthority('SCOPE_batch.read')") public Object runs(){return Map.of("runs",db.queryForList("select execution_id,run_key,retries from batch_runs order by execution_id desc limit 100"));}
 @GetMapping("/runs/{id}") @PreAuthorize("hasAuthority('SCOPE_batch.read')") public Object status(@PathVariable long id){var run=explorer.getJobExecution(id);if(run==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);String key=run.getJobParameters().getString("runKey");var steps=run.getStepExecutions().stream().map(s->Map.of("step",s.getStepName(),"status",s.getStatus().name(),"read",s.getReadCount(),"written",s.getWriteCount(),"rollbacks",s.getRollbackCount())).toList();return Map.of("executionId",id,"status",run.getStatus().name(),"runKey",key,"records",db.queryForList("select status,count(*) as count from batch_records where run_key=? group by status",key),"steps",steps,"history",db.queryForList("select * from batch_runs where run_key=? order by execution_id",key));}
 @GetMapping(value="/runs/{id}/{file}",produces="text/csv") @PreAuthorize("hasAuthority('SCOPE_batch.read')") public byte[] download(@PathVariable long id,@PathVariable String file)throws Exception{if(!Set.of("resultado.csv","rechazados.csv","detalle.csv").contains(file))throw new ResponseStatusException(HttpStatus.NOT_FOUND);var run=explorer.getJobExecution(id);if(run==null||run.getStatus()!=BatchStatus.COMPLETED)throw new ResponseStatusException(HttpStatus.CONFLICT,"Salida disponible al completar el job");return Files.readAllBytes(out.resolve(run.getJobParameters().getString("runKey")).resolve(file));}
 @Scheduled(fixedDelay=15000,initialDelay=30000) public void restartFailed(){for(var row:db.queryForList("select * from batch_runs where retries<3 and next_attempt<=current_timestamp and stopped=false"))try{long id=((Number)row.get("execution_id")).longValue();var old=explorer.getJobExecution(id);if(old!=null&&old.getStatus()==BatchStatus.FAILED){int claimed=db.update("update batch_runs set stopped=true where execution_id=? and stopped=false",id);if(claimed==1){var next=launcher.run(jobs.get(old.getJobParameters().getString("kind")),old.getJobParameters());db.update("insert into batch_runs(execution_id,run_key,retries,next_attempt) values(?,?,?,current_timestamp+interval '30 seconds')",next.getId(),row.get("run_key"),((Number)row.get("retries")).intValue()+1);}}}catch(Exception e){try{db.update("update batch_runs set stopped=false,retries=retries+1,next_attempt=current_timestamp+interval '30 seconds' where execution_id=?",row.get("execution_id"));}catch(RuntimeException ignored){}org.slf4j.LoggerFactory.getLogger(getClass()).warn("Reinicio batch requiere revision: {}",e.getClass().getSimpleName());}}
}
@Configuration class LauncherConfiguration {
 @Bean("asyncLauncher") JobLauncher asyncLauncher(JobRepository r)throws Exception{var l=new TaskExecutorJobLauncher();l.setJobRepository(r);var executor=new SimpleAsyncTaskExecutor("batch-job-");executor.setConcurrencyLimit(1);l.setTaskExecutor(executor);l.afterPropertiesSet();return l;}
}
