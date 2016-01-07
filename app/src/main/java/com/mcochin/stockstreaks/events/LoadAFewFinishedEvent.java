package com.mcochin.stockstreaks.events;

import com.mcochin.stockstreaks.pojos.Stock;

import java.util.List;

/**
 * Created by Marco on 12/24/2015.
 */
public class LoadAFewFinishedEvent extends Event{
    private List<Stock> mStockList;
    private boolean mSuccessful;

    public LoadAFewFinishedEvent(String sessionId, List<Stock> stockList, boolean successful){
        super(sessionId);
        mStockList = stockList;
        mSuccessful = successful;
    }

    public List<Stock> getStockList() {
        return mStockList;
    }

    public void setStockList(List<Stock> stockList) {
        mStockList = stockList;
    }

    public boolean isSuccessful() {
        return mSuccessful;
    }

    public void setSuccessful(boolean successful) {
        mSuccessful = successful;
    }
}
