package com.momna.modules.auth.application;

public interface EmailChallengeDelivery {
    void deliver(String email, String code);
}
