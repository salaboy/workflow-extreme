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

import io.dapr.springboot.DaprAutoConfiguration;
import io.dapr.springboot.extreme.workflows.model.PaymentRequest;
import io.dapr.springboot.extreme.workflows.service.ActivityTrackerService;
import io.dapr.springboot.extreme.workflows.service.RetryLogService;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.dapr.workflows.client.WorkflowInstanceStatus;
import io.dapr.workflows.client.WorkflowRuntimeStatus;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/*
 * This test highlights catching exceptions and retry for timeouts

 */
@SpringBootTest(classes = { TestWorkflowsApplication.class, DaprTestContainersConfig.class,
    DaprAutoConfiguration.class }, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = { "tests.dapr.local=true" }) // this property is used to start dapr for this test
class SimpleWorkflowTests {


  @Autowired
  private RetryLogService retryLogService;

  @Autowired
  private ActivityTrackerService activityTrackerService;

  @Autowired
  private DaprWorkflowClient daprWorkflowClient;

  @BeforeEach
  void setUp() {
    RestAssured.baseURI = "http://localhost:" + 8080;
    org.testcontainers.Testcontainers.exposeHostPorts(8080);

  }

  @Test
  void testMultiRetryTimeoutsWorkflows() throws InterruptedException, IOException {

    retryLogService.resetRetryCounter();
    activityTrackerService.clearExecutedActivities();

    PaymentRequest paymentRequest = new PaymentRequest("123", "salaboy", 1);

    PaymentRequest paymentRequestResult = given().contentType(ContentType.JSON)
        .body(paymentRequest)
        .when()
        .post("/start")
        .then()
        .statusCode(200).extract().as(PaymentRequest.class);

    // Check that I have an instance id
    assertFalse(paymentRequestResult.getWorkflowInstanceId().isEmpty());

    //Let's send the START event
    given().contentType(ContentType.JSON)
            .queryParam("instanceId", paymentRequestResult.getWorkflowInstanceId())
            .body("test content")
            .when()
            .post("/event-start")
            .then()
            .statusCode(200);

    await().atMost(Duration.ofSeconds(30))
            .pollDelay(500, TimeUnit.MILLISECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .until(() -> {
                    System.out.println("Retry count so far: " + retryLogService.getRetryCounter());
                    return retryLogService.getRetryCounter() == 10;
            });

    // Check that the workflow completed successfully
    await().atMost(Duration.ofSeconds(20))
        .pollDelay(500, TimeUnit.MILLISECONDS)
        .pollInterval(500, TimeUnit.MILLISECONDS)
        .until(() -> {
          WorkflowInstanceStatus instanceState = daprWorkflowClient
              .getInstanceState(paymentRequestResult.getWorkflowInstanceId(), true);
          if (instanceState == null) {
            return false;
          }
          if (!instanceState.getRuntimeStatus().equals(WorkflowRuntimeStatus.COMPLETED)) {
            return false;
          }
          PaymentRequest paymentRequestResultFromWorkflow = instanceState.readOutputAs(PaymentRequest.class);
          return paymentRequestResultFromWorkflow != null;
        });

    assertEquals(12, activityTrackerService.getExecutedActivities().size());
    assertTrue(activityTrackerService.getExecutedActivities().contains(FirstActivity.class.getCanonicalName()));
    assertTrue(activityTrackerService.getExecutedActivities().contains(RetryActivity.class.getCanonicalName()));
    assertTrue(activityTrackerService.getExecutedActivities().contains(CompensationActivity.class.getCanonicalName()));


  }

  @Test
  void testMultiRetryWithCompensationWorkflows() throws InterruptedException, IOException {

    retryLogService.resetRetryCounter();
    activityTrackerService.clearExecutedActivities();

    PaymentRequest paymentRequest = new PaymentRequest("123", "salaboy", 1);

    PaymentRequest paymentRequestResult = given().contentType(ContentType.JSON)
            .body(paymentRequest)
            .when()
            .post("/start")
            .then()
            .statusCode(200).extract().as(PaymentRequest.class);



    // Check that I have an instance id
    assertFalse(paymentRequestResult.getWorkflowInstanceId().isEmpty());

    //Let's send the START event
    given().contentType(ContentType.JSON)
            .queryParam("instanceId", paymentRequestResult.getWorkflowInstanceId())
            .body("test content")
            .when()
            .post("/event-start")
            .then()
            .statusCode(200);


    await().atMost(Duration.ofSeconds(30))
            .pollDelay(500, TimeUnit.MILLISECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .until(() -> {
              System.out.println("Retry count so far: " + retryLogService.getRetryCounter());
              return retryLogService.getRetryCounter() == 3;
            });

    System.out.println(">>> Let's send an event");
    given().contentType(ContentType.JSON)
            .queryParam("instanceId", paymentRequestResult.getWorkflowInstanceId())
            .body("test content")
            .when()
            .post("/event-continue")
            .then()
            .statusCode(200);


    // Check that the workflow completed successfully
    await().atMost(Duration.ofSeconds(3))
            .pollDelay(500, TimeUnit.MILLISECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .until(() -> {
              WorkflowInstanceStatus instanceState = daprWorkflowClient
                      .getInstanceState(paymentRequestResult.getWorkflowInstanceId(), true);
              if (instanceState == null) {
                return false;
              }
              if (!instanceState.getRuntimeStatus().equals(WorkflowRuntimeStatus.COMPLETED)) {
                return false;
              }
              PaymentRequest paymentRequestResultFromWorkflow = instanceState.readOutputAs(PaymentRequest.class);
              return paymentRequestResultFromWorkflow != null;
            });

    System.out.println("Activities executed: " + activityTrackerService.getExecutedActivities());
    assertEquals(5, activityTrackerService.getExecutedActivities().size());
    assertTrue(activityTrackerService.getExecutedActivities().contains(FirstActivity.class.getCanonicalName()));
    assertTrue(activityTrackerService.getExecutedActivities().contains(RetryActivity.class.getCanonicalName()));
    assertTrue(activityTrackerService.getExecutedActivities().contains(NextActivity.class.getCanonicalName()));


  }

}
