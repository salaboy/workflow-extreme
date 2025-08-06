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

package io.dapr.springboot.extreme.workflows.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PaymentRequest {

  private String id;
  private String customer;
  private Integer amount;
  private String workflowInstanceId;
  private List<PaymentItem> paymentItems = new ArrayList<>();

  public PaymentRequest() {

  }

  public PaymentRequest(String id, String customer, Integer amount) {
    this.id = id;
    this.customer = customer;
    this.amount = amount;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getCustomer() {
    return customer;
  }

  public void setCustomer(String customer) {
    this.customer = customer;
  }

  public Integer getAmount() {
    return amount;
  }

  public void setAmount(Integer amount) {
    this.amount = amount;
  }

  public String getWorkflowInstanceId() {
    return workflowInstanceId;
  }

  public void setWorkflowInstanceId(String workflowInstanceId) {
    this.workflowInstanceId = workflowInstanceId;
  }

  public List<PaymentItem> getPaymentItems() {
    return paymentItems;
  }

  public void setPaymentItems(List<PaymentItem> paymentItems) {
    this.paymentItems = paymentItems;
  }

  @Override
  public String toString() {
    return "PaymentRequest{" +
            "id='" + id + '\'' +
            ", customer='" + customer + '\'' +
            ", amount=" + amount +
            ", workflowInstanceId='" + workflowInstanceId + '\'' +
            ", paymentItems=" + paymentItems +
            '}';
  }
}
