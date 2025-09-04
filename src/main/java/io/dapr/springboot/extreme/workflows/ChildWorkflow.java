package io.dapr.springboot.extreme.workflows;

import io.dapr.springboot.extreme.workflows.model.PaymentItem;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowStub;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dapr.workflows.WorkflowTaskRetryPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ChildWorkflow implements Workflow {

  private final MeterRegistry registry;

  private Timer.Sample firstChildActivityTimerSample = null;
  private Timer.Sample secondChildActivityTimerSample = null;
  private Timer.Sample childWorkflowTimerSample = null;
  public ChildWorkflow(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public WorkflowStub create() {
    return ctx -> {


      if (!ctx.isReplaying()) {
        childWorkflowTimerSample = Timer.start(registry);
      }

      String instanceId = ctx.getInstanceId();
      ctx.getLogger().info("Child Workflow instance {} started", instanceId);
      PaymentItem paymentItem = ctx.getInput(PaymentItem.class);


      ctx.getLogger().info("Let's call the first child activity for payment item {}.", paymentItem.getItemName());

      if (!ctx.isReplaying()) {
        firstChildActivityTimerSample = Timer.start(registry);
      }
      WorkflowTaskOptions taskOptions = new WorkflowTaskOptions(WorkflowTaskRetryPolicy
              .newBuilder()
              .setFirstRetryInterval(Duration.ofSeconds(5))
              .setRetryTimeout(Duration.ofSeconds(5))
              .setMaxNumberOfAttempts(3)
              .build());
      paymentItem = ctx.callActivity(FirstChildActivity.class.getName(), paymentItem, taskOptions,
              PaymentItem.class).await();

      if (!ctx.isReplaying()) {
        firstChildActivityTimerSample.stop(registry.timer("firstChildActivity.workflow", "workflow", "callActivity"));
      }

      ctx.getLogger().info("First Activity for payment item: {} completed.", paymentItem.getItemName());

      ctx.getLogger().info("Let's call the second child activity for payment item {}.", paymentItem.getItemName());

      if (!ctx.isReplaying()) {
        secondChildActivityTimerSample = Timer.start(registry);
      }
      paymentItem = ctx.callActivity(SecondChildActivity.class.getName(), paymentItem,
              PaymentItem.class).await();

      if (!ctx.isReplaying()) {
        secondChildActivityTimerSample.stop(registry.timer("secondChildActivity.workflow", "workflow", "callActivity"));
      }

      ctx.getLogger().info("First Activity for payment item: {} completed.", paymentItem.getItemName());

      ctx.complete(paymentItem);
      if (!ctx.isReplaying()) {
        childWorkflowTimerSample.stop(registry.timer("end-child.workflow", "workflow", "workflow"));
      }

    };
  }
}
