package cl.bancoxyz.customersservice;

import org.springframework.context.annotation.*;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.backoff.FixedBackOff;
@Configuration
public class ConsumerConfiguration {
 @Bean DefaultErrorHandler kafkaErrors(KafkaTemplate<String,String> k){return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(k,(r,e)->new org.apache.kafka.common.TopicPartition(r.topic()+".DLT",r.partition())),new FixedBackOff(1000,3));}
}
