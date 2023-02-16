/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.hetianyi.spring.cloud.consul.patch;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.OperationException;
import com.ecwid.consul.v1.agent.model.NewService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.consul.discovery.ConsulDiscoveryClient;
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties;
import org.springframework.cloud.consul.discovery.HeartbeatProperties;
import org.springframework.cloud.consul.discovery.ReregistrationPredicate;
import org.springframework.cloud.consul.discovery.TtlScheduler;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

/**
 * spring cloud consul patch scheduler
 *
 * @author Jason He
 */
public class PatchTtlScheduler extends TtlScheduler {

    private static final Log log = LogFactory.getLog(ConsulDiscoveryClient.class);

    private final Map<String, ScheduledFuture> serviceHeartbeats = new ConcurrentHashMap<>();

    private final TaskScheduler scheduler = new ConcurrentTaskScheduler(Executors.newSingleThreadScheduledExecutor());

    private final HeartbeatProperties heartbeatProperties;

    private final ConsulDiscoveryProperties discoveryProperties;

    private final ConsulClient client;

    private final ReregistrationPredicate reregistrationPredicate;

    private final Map<String, NewService> registeredServices = new ConcurrentHashMap<>();

    public PatchTtlScheduler(
            HeartbeatProperties heartbeatProperties,
            ConsulDiscoveryProperties discoveryProperties,
            ConsulClient client,
            ReregistrationPredicate reregistrationPredicate
    ) {
        super(heartbeatProperties, discoveryProperties, client, reregistrationPredicate);
        this.heartbeatProperties = heartbeatProperties;
        this.discoveryProperties = discoveryProperties;
        this.client = client;
        this.reregistrationPredicate = reregistrationPredicate;
    }

    public void add(final NewService service) {
        add(service.getId());
        this.registeredServices.put(service.getId(), service);
    }

    /**
     * Add a service to the checks loop.
     *
     * @param instanceId instance id
     */
    public void add(String instanceId) {
        ScheduledFuture task = this.scheduler.scheduleAtFixedRate(new ConsulHeartbeatTask(instanceId, this),
                computeHeartbeatInterval().toMillis());
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
        this.registeredServices.remove(instanceId);
    }

    static class ConsulHeartbeatTask implements Runnable {

        private final String serviceId;

        private final String checkId;

        private final PatchTtlScheduler ttlScheduler;

        ConsulHeartbeatTask(String serviceId, PatchTtlScheduler ttlScheduler) {
            this.serviceId = serviceId;
            if (!this.serviceId.startsWith("service:")) {
                this.checkId = "service:" + this.serviceId;
            } else {
                this.checkId = this.serviceId;
            }
            this.ttlScheduler = ttlScheduler;
        }

        @Override
        public void run() {
            try {
                this.ttlScheduler.client.agentCheckPass(
                        this.checkId,
                        null,
                        this.ttlScheduler.discoveryProperties.getAclToken()
                );
                if (log.isDebugEnabled()) {
                    log.debug("Sending consul heartbeat for: " + this.checkId);
                }
            } catch (OperationException e) {
                if (this.ttlScheduler.heartbeatProperties.isReregisterServiceOnFailure()
                        && this.ttlScheduler.reregistrationPredicate.isEligible(e)) {
                    log.warn(e.getMessage());
                    NewService registeredService = this.ttlScheduler.registeredServices.get(this.serviceId);
                    if (registeredService != null) {
                        if (log.isInfoEnabled()) {
                            log.info("Re-register " + registeredService);
                        }
                        this.ttlScheduler.client.agentServiceRegister(registeredService,
                                this.ttlScheduler.discoveryProperties.getAclToken());
                    } else {
                        log.warn("The service to re-register is not found.");
                    }
                } else {
                    throw e;
                }
            }
        }

    }


    protected Duration computeHeartbeatInterval() {
        // heartbeat rate at ratio * ttl, but no later than ttl -1s and, (under lesser
        // priority), no sooner than 1s from now
        double interval = this.heartbeatProperties.getTtl().getSeconds() * this.heartbeatProperties.getIntervalRatio();
        double max = Math.max(interval, 1);
        long ttlMinus1 = this.heartbeatProperties.getTtl().getSeconds() - 1;
        double min = Math.min(ttlMinus1, max);
        Duration heartbeatInterval = Duration.ofMillis(Math.round(1000 * min));
        log.debug("Computed heartbeatInterval: " + heartbeatInterval);
        return heartbeatInterval;
    }
}
