package io.dapr.springboot.extreme.workflows.model;

public class WorkflowInstance {
  private String instanceId;

  public WorkflowInstance() {
  }

  public WorkflowInstance(String instanceId) {
    this.instanceId = instanceId;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(String instanceId) {
    this.instanceId = instanceId;
  }

  @Override
  public String toString() {
    return "WorkflowInstance{" +
            "instanceId='" + instanceId + '\'' +
            '}';
  }
}
