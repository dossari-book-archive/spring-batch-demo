package com.example.springbatchdemo.config;

import com.example.springbatchdemo.tasklet.HelloTasklet;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
class BatchConfig {

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

    @Scheduled(cron = "${app.batch.cron}")
    public void runJob() throws Exception {
        var params = new JobParametersBuilder()
            .addLong("jobId", System.currentTimeMillis())
            .toJobParameters();
        jobLauncher.run(myJob(), params);
    }
}
