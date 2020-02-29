package com.github.hetianyi.spring.cloud.consul.patch;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.agent.model.NewService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties;
import org.springframework.cloud.consul.discovery.HeartbeatProperties;
import org.springframework.cloud.consul.discovery.TtlScheduler;
import org.springframework.cloud.consul.serviceregistry.ConsulAutoRegistration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;


/**
 * spring cloud consul patch scheduler
 *
 * @author Jason He
 * @version 1.0.0
 * @since 1.0.0
 * @date 2020-02-29
 */
public class PatchTtlScheduler extends TtlScheduler {

	private static final Log log = LogFactory.getLog(PatchTtlScheduler.class);

	private final Map<String, ScheduledFuture> serviceHeartbeats = new ConcurrentHashMap<>();

	private final TaskScheduler scheduler = new ConcurrentTaskScheduler(
			Executors.newSingleThreadScheduledExecutor());

	private HeartbeatProperties configuration;
	private ConsulClient client;
	private ConsulAutoRegistration reg;
	private ConsulDiscoveryProperties properties;

	public PatchTtlScheduler(HeartbeatProperties configuration, ConsulClient client,
			ConsulAutoRegistration reg, ConsulDiscoveryProperties properties) {
		super(configuration, client);
		this.configuration = configuration;
		this.reg = reg;
		this.client = client;
		this.properties = properties;
	}

	@Deprecated
	public void add(final NewService service) {
		add(service.getId());
	}

	/**
	 * Add a service to the checks loop.
	 * @param instanceId instance id
	 */
	public void add(String instanceId) {
		ScheduledFuture task = this.scheduler.scheduleAtFixedRate(
				new PatchTtlScheduler.ConsulHeartbeatTask(instanceId),
				computeHearbeatInterval(configuration.getTtlValue(), configuration.getIntervalRatio()));
		ScheduledFuture previousTask = this.serviceHeartbeats.put(instanceId, task);
		if (previousTask != null) {
			previousTask.cancel(true);
		}
	}

	public void remove(String instanceId) {
		ScheduledFuture task = this.serviceHeartbeats.get(instanceId);
		if (task != null) {
			task.cancel(true);
		}
		this.serviceHeartbeats.remove(instanceId);
	}

	private class ConsulHeartbeatTask implements Runnable {

		private String checkId;

		ConsulHeartbeatTask(String serviceId) {
			this.checkId = serviceId;
			if (!this.checkId.startsWith("service:")) {
				this.checkId = "service:" + this.checkId;
			}
		}

		@Override
		public void run() {
			try {
				PatchTtlScheduler.this.client.agentCheckPass(this.checkId);
			}
			catch (Exception e) {
				// try to re-register service, this may fail again.
				try {
					PatchTtlScheduler.this.client.agentServiceRegister(reg.getService(),
							PatchTtlScheduler.this.properties.getAclToken());
					if (log.isDebugEnabled()) {
						log.debug("Agent check failed for " + this.checkId
								+ ", re-registered");
					}
				}
				finally {
					throw e;
				}
			}
			if (log.isDebugEnabled()) {
				log.debug("Sending consul heartbeat for: " + this.checkId);
			}
		}

	}

	protected Duration computeHearbeatInterval(long ttlValue, double intervalRatio) {
		double interval = (double) ttlValue * intervalRatio;
		double max = Math.max(interval, 1.0D);
		long ttlMinus1 = ttlValue - 1;
		double min = Math.min((double) ttlMinus1, max);
		Duration heartbeatInterval = Duration.ofMillis(Math.round(1000 * min));
		log.debug("Computed heartbeatInterval: " + heartbeatInterval);
		return heartbeatInterval;
	}
}
