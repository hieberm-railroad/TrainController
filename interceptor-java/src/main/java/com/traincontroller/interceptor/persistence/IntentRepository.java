package com.traincontroller.interceptor.persistence;

public interface IntentRepository {
    String upsert(IntentEntity intent);
}
