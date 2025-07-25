package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Profile(PROFILE_CORE)
@Configuration
@Lazy
@EnableScheduling
public class TaskSchedulingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TaskSchedulingConfiguration.class);

    /**
     * Create a Task Scheduler with virtual threads and a pool size of 4.
     *
     * @return the Task Scheduler bean that can be injected into any service to schedule tasks
     */
    @Bean(name = "taskScheduler")
    public TaskScheduler taskScheduler() {
        log.debug("Creating Task Scheduler ");
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setVirtualThreads(true);
        scheduler.setPoolSize(4);
        scheduler.initialize();
        return scheduler;
    }
}
