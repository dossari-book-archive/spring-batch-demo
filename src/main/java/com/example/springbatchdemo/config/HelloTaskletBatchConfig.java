package com.example.springbatchdemo.config;

import com.example.springbatchdemo.tasklet.HelloTasklet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.batch.name", havingValue = "ImportBatch")
class HelloTaskletBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final HelloTasklet helloTasklet;
    private final JobLauncher jobLauncher;

    @Bean
    public Step myStep() {
        return new StepBuilder("myStep", jobRepository)
            .tasklet(helloTasklet, transactionManager)
            .build();
    }

    @Bean
    public Job myJob() {
        return new JobBuilder("myJob", jobRepository)
            .start(myStep())
            .build();
    }

    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            var jobParameters = new JobParametersBuilder()
                .addLong("jobId", System.currentTimeMillis())
                .toJobParameters();
            log.info("=========== start ===========");
            jobLauncher.run(myJob(), jobParameters);
        };
    }
}
