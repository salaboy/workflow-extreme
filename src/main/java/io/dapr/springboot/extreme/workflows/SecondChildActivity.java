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


import io.dapr.springboot.extreme.workflows.model.PaymentItem;
import io.dapr.springboot.extreme.workflows.service.ActivityTrackerService;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class SecondChildActivity implements WorkflowActivity {

  private final Logger logger = LoggerFactory.getLogger(SecondChildActivity.class);

  private final Timer activityTimer;

  private final ActivityTrackerService activityTrackerService;

  public SecondChildActivity(ActivityTrackerService activityTrackerService, MeterRegistry registry) {
    this.activityTrackerService = activityTrackerService;
    this.activityTimer = Timer.builder("activity.second-child-activity")
            .description("Time for SecondChildActivity activity execution")
            .tags("workflow", "SecondChildActivity")
            .register(registry);
  }

  @Override
  public Object run(WorkflowActivityContext ctx) {
    return activityTimer.record(() -> {
      PaymentItem paymentItem = ctx.getInput(PaymentItem.class);

      logger.info("Executing Second Child Activity.");
      activityTrackerService.addExecutedActivity(ctx.getName());
      paymentItem.setItemName(paymentItem.getItemName() + "-2");
      logger.info("Second Child Activity: {}", paymentItem.getItemName());
      return paymentItem;
    });
  }


}
