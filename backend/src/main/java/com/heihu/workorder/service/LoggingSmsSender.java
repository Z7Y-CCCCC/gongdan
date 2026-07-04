package com.heihu.workorder.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    private final boolean logCode;

    public LoggingSmsSender(boolean logCode) {
        this.logCode = logCode;
    }

    @Override
    public void sendVerificationCode(String phone, String code) {
        if (logCode) {
            log.warn("SMS code generated for local use [phone={}, code={}]", phone, code);
            return;
        }
        log.info("SMS code generated [phone={}]", phone);
    }
}
