package com.bj.marketdata.admin;

import com.bj.marketdata.consumer.Subscription;

import java.util.Map;
import java.util.Optional;

public interface AdminService {
    ProcessingProgress currentProgress();

    long rejectedUpdates();

    Optional<Subscription> subscriptionByClient(String clientId);

    Map<String, Subscription> allSubscriptions();
}
