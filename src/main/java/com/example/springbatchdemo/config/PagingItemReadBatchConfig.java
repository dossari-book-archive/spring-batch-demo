package com.example.springbatchdemo.config;

import com.example.springbatchdemo.entity.Person;
import java.util.LinkedHashMap;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.ItemReadListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.batch.name", havingValue = "PagingItemReadBatch")
class PagingItemReadBatchConfig {

    private final JobRepository jobRepository;
    private final JobLauncher jobLauncher;
    private final DataSource dataSource;
    private final PlatformTransactionManager platformTransactionManager;
    private @Value("${app.output.file}") String outputFile;

    @Bean
    // @StepScope // step単位でJDBCリソースが適切にクローズされるようにするために必要？
    public ItemReader<Person> reader() {
        var reader = new JdbcPagingItemReader<Person>();
        reader.setDataSource(dataSource);
        reader.setPageSize(10); // ページサイズを設定

        var queryProvider = queryProvider();
        reader.setQueryProvider(queryProvider);
        reader.setRowMapper(new BeanPropertyRowMapper<>(Person.class));
        return reader;
    }

    @Bean
    // @StepScope // readerに合わせて、念のためこちらも入れておく
    public FlatFileItemWriter<Person> writer() {
        // lineAggregator
        var lineAggregator = new DelimitedLineAggregator<Person>();
        lineAggregator.setDelimiter(",");
        lineAggregator.setFieldExtractor(person ->
            new Object[]{
                person.getId(),
                person.getName(),
                person.getAge(),
                person.getGender()
            }
        );
        // writer
        var writer = new FlatFileItemWriter<Person>();
        writer.setResource(new FileSystemResource(outputFile));
        writer.setLineAggregator(lineAggregator);
        return writer;
    }

    @Bean
    public PagingQueryProvider queryProvider() {
        var queryProvider = new SqlPagingQueryProviderFactoryBean();
        queryProvider.setDataSource(dataSource); // データソースの設定
        queryProvider.setSelectClause("SELECT id, name, age, gender");
        queryProvider.setFromClause("FROM person");
        var sortKey = new LinkedHashMap<String, Order>();
        sortKey.put("age", Order.ASCENDING);
        sortKey.put("id", Order.ASCENDING);
        queryProvider.setSortKeys(sortKey);
        try {
            return queryProvider.getObject();
        } catch (Exception e) {
            // 基本発生しない想定
            throw new RuntimeException(e);
        }
    }

    @Bean
    public Step step1() {
        // stepごとにshutdownが必要？
        // job終了時にshutdownを行わないと、Javaプロセスが生き残り続けてしまう
        var taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(4);
        taskExecutor.setMaxPoolSize(4);
        taskExecutor.setThreadNamePrefix("batch-thread-");
        taskExecutor.initialize();

        return new StepBuilder("step1", jobRepository)
            .<Person, Person>chunk(10, platformTransactionManager)
            .reader(reader())
            .writer(writer())
            .taskExecutor(taskExecutor)
            .listener(new PagingItemReadListener<>())
            .listener(new StepExecutionListener() {
                @Override
                public ExitStatus afterStep(StepExecution stepExecution) {
                    taskExecutor.shutdown();
                    return null; // 終了時のステータスは上書きしない
                }
            })
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

    @Slf4j
    private static class PagingItemReadListener<T> implements ItemReadListener<T> {

        @Override
        public void afterRead(T item) {
            log.debug("read: " + item); // 本番では出力しない
        }

        @Override
        public void onReadError(Exception e) {
            log.error("Error reading page: ", e);
        }
    }
}
