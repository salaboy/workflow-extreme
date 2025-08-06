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

package io.dapr.springboot.extreme.workflows.service;

import io.dapr.springboot.extreme.workflows.model.PaymentRequest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PaymentWorkflowsStore {

  private final Map<String, String> paymentsWorkflows = new HashMap<>();

  public void savePaymentWorkflow(PaymentRequest paymentRequest, String instanceId) {
    paymentsWorkflows.put(paymentRequest.getId(), instanceId);
  }

  public String getPaymentWorkflowInstanceId(String paymentRequestId) {
    return paymentsWorkflows.get(paymentRequestId);
  }

  public Map<String, String> getPaymentWorkflows() {
    return paymentsWorkflows;
  }

}
