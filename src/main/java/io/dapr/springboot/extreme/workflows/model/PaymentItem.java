package io.dapr.springboot.extreme.workflows.model;

public class PaymentItem {
  private String itemName;

  public PaymentItem() {
  }

  public PaymentItem(String itemName) {
    this.itemName = itemName;
  }

  public String getItemName() {
    return itemName;
  }

  public void setItemName(String itemName) {
    this.itemName = itemName;
  }
}
