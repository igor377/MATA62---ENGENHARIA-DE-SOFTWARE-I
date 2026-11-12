package br.ufba.veridt.notification.internal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

	public static final String EXECUTOR = "notificationExecutor";

	/**
	 * Executor exclusivo dos e-mails. Cada envio ocupa uma conexão com o banco enquanto espera o SMTP; limitar as threads
	 * impede que uma queda do servidor de e-mail esgote o pool de conexões do resto da aplicação.
	 */
	@Bean(EXECUTOR)
	ThreadPoolTaskExecutor notificationExecutor(NotificationProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(properties.threads());
		executor.setMaxPoolSize(properties.threads());
		executor.setThreadNamePrefix("notification-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(10);
		return executor;
	}

}
