package com.heihu.workorder.dto;

public class MessageDeliveryTestResponse {
    private final boolean success;
    private final String message;
    private final Integer httpStatus;
    private final ExtensionRecordResponse record;

    public MessageDeliveryTestResponse(boolean success, String message, Integer httpStatus, ExtensionRecordResponse record) {
        this.success = success; this.message = message; this.httpStatus = httpStatus; this.record = record;
    }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Integer getHttpStatus() { return httpStatus; }
    public ExtensionRecordResponse getRecord() { return record; }
}
