package io.dapr.springboot.extreme.workflows.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RetryLogService {
  private AtomicInteger retryCounter = new AtomicInteger();

  public void incrementRetryCounter(){
    retryCounter.addAndGet(1);
  }

  public int getRetryCounter(){
    return retryCounter.get();
  }

  public void resetRetryCounter(){
    retryCounter.set(0);
  }
}
