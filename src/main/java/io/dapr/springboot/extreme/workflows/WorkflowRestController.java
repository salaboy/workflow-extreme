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

import io.dapr.springboot.extreme.workflows.model.WorkflowInstance;
import io.dapr.springboot.extreme.workflows.service.ActivityTrackerService;
import io.dapr.springboot.extreme.workflows.service.RetryLogService;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@RestController
@EnableDaprWorkflows

public class WorkflowRestController {

  private final Logger logger = LoggerFactory.getLogger(WorkflowRestController.class);

  @Autowired
  private DaprWorkflowClient daprWorkflowClient;

  @Autowired
  private RetryLogService retryLogService;

  @Autowired
  private RestTemplate restTemplate;

  private final KafkaTemplate<String, Object> kafkaTemplate;

  @Value("CATALYST_API_KEY")
  private String CATALYST_API_KEY;

  @Autowired
  private ActivityTrackerService activityTrackerService;

  private final Timer startWorkflowTimer;

  private final Timer raiseEventWorkflowTimer;

  @Value("${REMOTE_KAFKA_TOPIC:topic}")
  private String kafkaTopic;

  public WorkflowRestController(MeterRegistry registry, KafkaTemplate<String, Object> kafkaTemplate) {
    startWorkflowTimer = Timer.builder("start.workflow")
            .description("Time start workflow execution")
            .tags("workflow", "start")
            .register(registry);

    raiseEventWorkflowTimer = Timer.builder("event.workflow")
            .description("Time raise event workflow")
            .tags("workflow", "event")
            .register(registry);
    this.kafkaTemplate = kafkaTemplate;
  }

  /**
   *  Start workflow
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

  /**
   *  Start workflow
   *
   * @param paymentRequest to be sent to a remote http service
   * @return workflow instance id created for the payment
   */
  @PostMapping("/start-kafka")
  public PaymentRequest placePaymentRequestWithKafka(@RequestBody PaymentRequest paymentRequest) {
    retryLogService.resetRetryCounter();
    activityTrackerService.clearExecutedActivities();

    String instanceId = startWorkflowTimer.record(() -> daprWorkflowClient
            .scheduleNewWorkflow(SimpleWorkflow.class, paymentRequest));
    paymentRequest.setWorkflowInstanceId(instanceId);

    kafkaTemplate.send(kafkaTopic, instanceId);
    return paymentRequest;
  }

  @PostMapping("/event-start")
  public String eventStart(@RequestBody String content, @RequestParam("instanceId") String instanceId) {
    logger.info("Event received with content {}.", content);
    raiseEventWorkflowTimer.record(() ->
            daprWorkflowClient.raiseEvent(instanceId, "START-EVENT", content));
    return "Event processed";
  }

  @PostMapping("/event-continue")
  public String eventContinue(@RequestBody String content, @RequestParam("instanceId") String instanceId) {
    logger.info("Event received with content {}.", content);
    raiseEventWorkflowTimer.record(() ->
            daprWorkflowClient.raiseEvent(instanceId, "CONTINUE-EVENT", content));
    return "Event processed";
  }


  @DeleteMapping("/delete")
  public void terminate(@RequestParam("instanceId") String instanceId){
    daprWorkflowClient.terminateWorkflow(instanceId, null);
  }

  @GetMapping("catalyst/instances")
  public List<WorkflowInstance> getWorkflowInstancesFromCatalyst(){
    String catalystInstance = "management.jpmc-us-east-1.private.cloud.diagrid.io";
    String catalystProject = "payments-smb-workflows";
    String statuses = "pending";
    Integer limit = 20;
    String url = new StringBuilder()
            .append("https://").append(catalystInstance)
            .append("/apis/cra.diagrid.io/v1beta2/projects/")
            .append(catalystProject)
            .append("/workflowexecutions")
            .append("?limit=")
            .append(limit)
            .append("&status[]=")
            .append(statuses)
            .toString();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("x-diagrid-api-key", CATALYST_API_KEY);
    HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

    ResponseEntity<WorkflowInstance[]> response = restTemplate.exchange(
            url, HttpMethod.GET, requestEntity, WorkflowInstance[].class);

    System.out.println(response.toString());
    return null;
  }

}

