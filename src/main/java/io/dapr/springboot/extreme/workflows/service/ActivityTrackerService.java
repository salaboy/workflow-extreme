package io.dapr.springboot.extreme.workflows.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ActivityTrackerService {
  private final List<String> executedActivities = new ArrayList<>();

  public void addExecutedActivity(String activityName){
    executedActivities.add(activityName);
  }

  public List<String> getExecutedActivities(){
    return executedActivities;
  }

  public void clearExecutedActivities(){
    executedActivities.clear();
  }
}