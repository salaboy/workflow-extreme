package io.dapr.springboot.extreme.workflows;


import io.dapr.springboot.extreme.workflows.model.PaymentRequest;
import io.dapr.springboot.extreme.workflows.service.PaymentWorkflowsStore;
import io.dapr.workflows.client.DaprWorkflowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ExternalKafkaMessageListener {

  private final Logger logger = LoggerFactory.getLogger(ExternalKafkaMessageListener.class);

  @Autowired
  private DaprWorkflowClient daprWorkflowClient;

  @Autowired
  private PaymentWorkflowsStore paymentWorkflowsStore;


  /**
   *  Wait for async kafka message
   */
  @KafkaListener(topics = "${REMOTE_KAFKA_TOPIC:topic}", groupId = "workflows")
  public void kafkaEventListener(String workflowInstanceId) {
    logger.info("Event received, triggering raiseEvent: " + workflowInstanceId);
    daprWorkflowClient.raiseEvent(workflowInstanceId, "START-EVENT", "event content");

  }
}
