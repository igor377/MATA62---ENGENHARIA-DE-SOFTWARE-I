package br.ufba.veridt.shared.audit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.ThreadPoolTaskSchedulerCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditProperties.class)
public class AuditConfig {

	@Bean
	ClientAddressResolver clientAddressResolver(AuditProperties properties) {
		return new ClientAddressResolver(properties.trustedProxies());
	}

	@Bean
	FilterRegistrationBean<RequestIdFilter> requestIdFilter(ClientAddressResolver addressResolver) {
		FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(
				new RequestIdFilter(addressResolver));
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}

	/** O Boot aplica este decorador ao executor padrão, usado pelo {@code @Async}. */
	@Bean
	TaskDecorator auditContextTaskDecorator() {
		return new AuditContextTaskDecorator(AuditContext.ASYNC_PROCESS);
	}

	/** O Boot também aplicaria o decorador acima ao agendador; este customizador roda depois e o substitui. */
	@Bean
	ThreadPoolTaskSchedulerCustomizer auditContextSchedulerCustomizer() {
		return scheduler -> scheduler.setTaskDecorator(new AuditContextTaskDecorator(AuditContext.SCHEDULED_TASK));
	}

}
