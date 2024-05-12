package com.example.springbatchdemo.config;

import com.example.springbatchdemo.entity.Person;
import com.example.springbatchdemo.tasklet.HelloTasklet;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
class BatchConfig {

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
        reader.setSql("SELECT id, name, age FROM person ORDER BY age, id");
        reader.setRowMapper(new BeanPropertyRowMapper<>(Person.class));
        return reader;
    }

    @Bean
    public FlatFileItemWriter<Person> writer() {
        var writer = new FlatFileItemWriter<Person>();
        writer.setResource(new FileSystemResource(outputFile));
        writer.setLineAggregator(new DelimitedLineAggregator<>() {{
            setDelimiter(",");
            setFieldExtractor(new BeanWrapperFieldExtractor<>() {{
                setNames(new String[]{"id", "name", "age"});
            }});
        }});
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
            jobLauncher.run(exportPersonJob(), jobParameters);
        };
    }
}
