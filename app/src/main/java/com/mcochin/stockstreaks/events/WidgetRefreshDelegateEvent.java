package com.mcochin.stockstreaks.events;

/**
 * Created by Marco on 1/3/2016.
 */
public class WidgetRefreshDelegateEvent extends Event{
    public WidgetRefreshDelegateEvent(String sessionId){
        super(sessionId);
    }
}
