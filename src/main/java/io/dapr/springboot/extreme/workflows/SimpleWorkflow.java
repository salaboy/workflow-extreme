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

import io.dapr.durabletask.Task;
import io.dapr.durabletask.TaskCanceledException;
import io.dapr.springboot.extreme.workflows.model.PaymentItem;
import io.dapr.springboot.extreme.workflows.model.PaymentRequest;
import io.dapr.springboot.extreme.workflows.service.RetryLogService;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowStub;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SimpleWorkflow implements Workflow {

  @Autowired
  private RetryLogService retryLogService;

  private Timer.Sample workflowTimerSample = null;
  private Timer.Sample firstActivityTimerSample = null;
  private Timer.Sample nextActivityTimerSample = null;
  private Timer.Sample retryActivityTimerSample = null;
  private Timer.Sample compensationActivityTimerSample = null;

  private final Timer childWorkflowTimer;

  private final MeterRegistry registry;

  public SimpleWorkflow(MeterRegistry registry) {
    this.registry = registry;
    this.childWorkflowTimer = Timer.builder("workflow.child-workflow")
            .description("Time for ChildWorkflow  execution")
            .tags("workflow", "ChildWorkflow")
            .register(registry);
  }

  @Override
  public WorkflowStub create() {
    return ctx -> {

      if (!ctx.isReplaying()) {
        workflowTimerSample = Timer.start(registry);
      }

      String instanceId = ctx.getInstanceId();

      ctx.getLogger().info("Workflow instance {} started", instanceId);
      PaymentRequest paymentRequest = ctx.getInput(PaymentRequest.class);


      ctx.getLogger().info("Let's wait for the START-EVENT to start processing payment: {}.", paymentRequest.getId());
      //Waiting on this event to start processing
      ctx.waitForExternalEvent("START-EVENT", String.class).await();


      ctx.getLogger().info("Let's call the first activity for payment: {}.", paymentRequest.getId());

      if (!ctx.isReplaying()) {
        firstActivityTimerSample = Timer.start(registry);
      }
      paymentRequest = ctx.callActivity(FirstActivity.class.getName(), paymentRequest,
              PaymentRequest.class).await();

      if (!ctx.isReplaying()) {
        firstActivityTimerSample.stop(registry.timer("firstActivity.workflow", "workflow", "callActivity"));
      }

      ctx.getLogger().info("First Activity for payment: {} completed.", paymentRequest.getId());

      ctx.getLogger().info("Let's create a child workflow per paymentItem {}.", paymentRequest.getPaymentItems());
      List<Task<PaymentItem>> tasks = paymentRequest.getPaymentItems().stream()
              .map(pi -> childWorkflowTimer.record(()->
                      ctx.callChildWorkflow(ChildWorkflow.class.getName(), pi, PaymentItem.class)))
              .collect(Collectors.toList());

      ctx.getLogger().info("All child workflows created.");

      ctx.getLogger().info("Let's wait for all child workflows to complete.");
      List<PaymentItem> allModifiedPaymentItems = ctx.allOf(tasks).await();
      paymentRequest.setPaymentItems(allModifiedPaymentItems);
      ctx.getLogger().info("All modified payment items from child workflows: {}", allModifiedPaymentItems);

      String eventContent = "";

      for (int i = 0; i < 10; i++) {
        try {
          ctx.getLogger().info("Wait for event, for 2 seconds, iteration: {}.", i);
          eventContent = ctx.waitForExternalEvent("CONTINUE-EVENT", Duration.ofSeconds(2), String.class).await();
          ctx.getLogger().info("Event arrived with content: {}", eventContent);
          //We got the event, so we can break the for loop.
          break;
        } catch (TaskCanceledException tce) {
          if (!ctx.isReplaying()) {
            retryLogService.incrementRetryCounter();
          }
          ctx.getLogger().info("Wait for event timed out. ");
          ctx.getLogger().info("Let's execute the Retry Activity. Retry: {}", retryLogService.getRetryCounter());

          if (!ctx.isReplaying()) {
            retryActivityTimerSample = Timer.start(registry);
          }
          paymentRequest = ctx.callActivity(RetryActivity.class.getName(), paymentRequest,
                  PaymentRequest.class).await();
          if (!ctx.isReplaying()) {
            retryActivityTimerSample.stop(registry.timer("retryActivity.workflow", "workflow", "callActivity"));
          }
          ctx.getLogger().info("Retry Activity executed successfully. ");
        }
      }

      if (eventContent.isEmpty()) {
        ctx.getLogger().info("Retries exhausted after {} retries. ", retryLogService.getRetryCounter());
        ctx.getLogger().info("Let's execute the Compensation Activity. ");
        if (!ctx.isReplaying()) {
          compensationActivityTimerSample = Timer.start(registry);
        }
        paymentRequest = ctx.callActivity(CompensationActivity.class.getName(), paymentRequest,
                PaymentRequest.class).await();
        if (!ctx.isReplaying()) {
          compensationActivityTimerSample.stop(registry.timer("compensationActivity.workflow", "workflow", "callActivity"));
        }
        ctx.getLogger().info("Compensation Activity executed successfully. ");
      } else {
        ctx.getLogger().info("We got the event after {} retries, let's execute the Next Activity. ", retryLogService.getRetryCounter());
        if (!ctx.isReplaying()) {
          nextActivityTimerSample = Timer.start(registry);
        }
        paymentRequest = ctx.callActivity(NextActivity.class.getName(), paymentRequest,
                PaymentRequest.class).await();
        if (!ctx.isReplaying()) {
          nextActivityTimerSample.stop(registry.timer("nextActivity.workflow", "workflow", "callActivity"));
        }
        ctx.getLogger().info("Next activity executed successfully. ");
      }


      ctx.complete(paymentRequest);
      if (!ctx.isReplaying()) {
        workflowTimerSample.stop(registry.timer("end.workflow", "workflow", "workflow"));
      }
      ctx.getLogger().info("Workflow {} Completed. ", paymentRequest.getId());
    };
  }
}


