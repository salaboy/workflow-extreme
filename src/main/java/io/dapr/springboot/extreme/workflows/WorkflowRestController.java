/*
 * Copyright 2025 The Dapr Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
limitations under the License.
*/

package io.dapr.springboot.extreme.workflows;

import io.dapr.spring.workflows.config.EnableDaprWorkflows;
import io.dapr.springboot.extreme.workflows.model.PaymentRequest;

import io.dapr.springboot.extreme.workflows.service.ActivityTrackerService;
import io.dapr.springboot.extreme.workflows.service.RetryLogService;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@EnableDaprWorkflows
public class WorkflowRestController {

  private final Logger logger = LoggerFactory.getLogger(WorkflowRestController.class);

  @Autowired
  private DaprWorkflowClient daprWorkflowClient;

  @Autowired
  private RetryLogService retryLogService;

  @Autowired
  private ActivityTrackerService activityTrackerService;

  private final Timer startWorkflowTimer;

  private final Timer raiseEventWorkflowTimer;

  public WorkflowRestController(MeterRegistry registry) {
    startWorkflowTimer = Timer.builder("start.workflow")
            .description("Time start workflow execution")
            .tags("workflow", "start")
            .register(registry);

    raiseEventWorkflowTimer = Timer.builder("event.workflow")
            .description("Time raise event workflow")
            .tags("workflow", "event")
            .register(registry);
  }

  /**
   * Multi retry Payment workflow
   *
   * @param paymentRequest to be sent to a remote http service
   * @return workflow instance id created for the payment
   */
  @PostMapping("/start")
  public PaymentRequest placePaymentRequest(@RequestBody PaymentRequest paymentRequest) {
    retryLogService.resetRetryCounter();
    activityTrackerService.clearExecutedActivities();

    String instanceId = startWorkflowTimer.record(() -> daprWorkflowClient
            .scheduleNewWorkflow(SimpleWorkflow.class, paymentRequest));
    paymentRequest.setWorkflowInstanceId(instanceId);
    return paymentRequest;
  }

  @PostMapping("/event-start")
  public String event(@RequestBody String content, @RequestParam("instanceId") String instanceId) {
    logger.info("Event received with content {}.", content);
    raiseEventWorkflowTimer.record(() ->
            daprWorkflowClient.raiseEvent(instanceId, "START-EVENT", content));
    return "Event processed";
  }

  @PostMapping("/event-continue")
  public String eventContinue(@RequestBody String content, @RequestParam("instanceId") String instanceId) {
    logger.info("Event received with content {}.", content);
    raiseEventWorkflowTimer.record(() ->
            daprWorkflowClient.raiseEvent(instanceId, "CONTINUE-EVENT",
                    content));
    return "Event processed";
  }

  @DeleteMapping("/delete")
  public void terminate(@RequestParam("instanceId") String instanceId){
    daprWorkflowClient.terminateWorkflow(instanceId, null);
  }

}

