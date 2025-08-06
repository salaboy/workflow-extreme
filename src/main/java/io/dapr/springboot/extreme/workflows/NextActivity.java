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


import io.dapr.springboot.extreme.workflows.model.PaymentRequest;
import io.dapr.springboot.extreme.workflows.service.ActivityTrackerService;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class NextActivity implements WorkflowActivity {

  private final Logger logger = LoggerFactory.getLogger(NextActivity.class);

  private final ActivityTrackerService activityTrackerService;
  private final Timer activityTimer;

  public NextActivity(ActivityTrackerService activityTrackerService, MeterRegistry registry) {
    this.activityTrackerService = activityTrackerService;
    this.activityTimer = Timer.builder("activity.next-activity")
            .description("Time for NextActivity activity execution")
            .tags("workflow", "NextActivity")
            .register(registry);
  }

  @Override
  public Object run(WorkflowActivityContext ctx) {
    return activityTimer.record(() -> {
      PaymentRequest paymentRequest = ctx.getInput(PaymentRequest.class);

      logger.info("Executing Next Activity.");

      activityTrackerService.addExecutedActivity(ctx.getName());

      return paymentRequest;
    });
  }


}
