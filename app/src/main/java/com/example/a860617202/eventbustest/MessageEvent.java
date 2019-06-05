package com.example.a860617202.eventbustest;

/**
 * 修改番号INLS-NEW-201902-002  RA对应 XieJW 2019/5/13 ADD
 */
public class MessageEvent {
    public String message;

    public MessageEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
