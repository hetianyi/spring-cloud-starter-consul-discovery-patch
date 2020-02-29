package com.github.hetianyi.spring.cloud.consul.patch;

import com.ecwid.consul.v1.ConsulClient;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.consul.ConditionalOnConsulEnabled;
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties;
import org.springframework.cloud.consul.discovery.HeartbeatProperties;
import org.springframework.cloud.consul.discovery.TtlScheduler;
import org.springframework.cloud.consul.serviceregistry.ConsulAutoRegistration;
import org.springframework.cloud.consul.serviceregistry.ConsulServiceRegistryAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnConsulEnabled
@ConditionalOnDiscoveryEnabled
@AutoConfigureAfter(ConsulServiceRegistryAutoConfiguration.class)
@ConditionalOnProperty("spring.cloud.consul.discovery.heartbeat.enabled")
public class PatchAutoConfiguration {

	@Bean
	@Primary
	public TtlScheduler patchTtlScheduler(
			HeartbeatProperties heartbeatProperties,
			ConsulClient consulClient,
			ConsulAutoRegistration consulAutoRegistration,
			ConsulDiscoveryProperties properties) {
		return new PatchTtlScheduler(heartbeatProperties, consulClient, consulAutoRegistration, properties);
	}
}
