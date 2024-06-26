package com.example.springbatchdemo.config;

import com.example.springbatchdemo.entity.Person;
import com.example.springbatchdemo.tasklet.HelloTasklet;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.batch.name", havingValue = "CursorItemReadBatch")
class CursorItemReadBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final HelloTasklet helloTasklet;
    private final JobLauncher jobLauncher;
    private final DataSource dataSource;
    private final PlatformTransactionManager platformTransactionManager;
    private @Value("${app.output.file}") String outputFile;

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
    public JdbcCursorItemReader<Person> reader() {
        var reader = new JdbcCursorItemReader<Person>();
        reader.setDataSource(dataSource);
        reader.setSql("SELECT id, name, age, gender FROM person ORDER BY age, id");
        reader.setRowMapper(new BeanPropertyRowMapper<>(Person.class));
        return reader;
    }

    @Bean
    public FlatFileItemWriter<Person> writer() {
        // fieldExtractor
        var fieldExtractor = new BeanWrapperFieldExtractor<Person>();
        fieldExtractor.setNames(new String[]{"id", "name", "age", "genderLabel"});
        // lineAggregator
        var lineAggregator = new DelimitedLineAggregator<Person>();
        lineAggregator.setDelimiter(",");
        lineAggregator.setFieldExtractor(fieldExtractor);
        // writer
        var writer = new FlatFileItemWriter<Person>();
        writer.setResource(new FileSystemResource(outputFile));
        writer.setLineAggregator(lineAggregator);
        return writer;
    }

    @Bean
    public Step step1() {
        return new StepBuilder("step1", jobRepository)
            .<Person, Person>chunk(10, platformTransactionManager)
            .reader(reader())
            .writer(writer())
            .build();
    }

    @Bean
    public Job exportPersonJob() {
        return new JobBuilder("exportPersonJob", jobRepository)
            .flow(step1())
            .end()
            .build();
    }

    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            var jobParameters = new JobParametersBuilder()
                .addLong("jobId", System.currentTimeMillis())
                .toJobParameters();
            log.info("=========== start ===========");
            jobLauncher.run(exportPersonJob(), jobParameters);
        };
    }
}
