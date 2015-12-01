package com.mcochin.stockstreaks.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.mcochin.stockstreaks.data.StockContract.StockEntry;
import com.mcochin.stockstreaks.data.StockContract.UpdateDateEntry;

import java.util.Calendar;
import java.util.TimeZone;

import yahoofinance.YahooFinance;

/**
 * Utility class containing general helper methods for this application
 */
public class Utility {
    private static final int STOCK_MARKET_UPDATE_HOUR = 16;
    private static final int STOCK_MARKET_UPDATE_MINUTE = 30;
    private static final int STOCK_MARKET_OPEN_HOUR = 9;
    private static final int STOCK_MARKET_OPEN_MINUTE = 30;

    /**
     * Returns true if the network is available or about to become available.
     *
     * @param c Context used to get the ConnectivityManager
     * @return true if the network is available
     */
    public static boolean isNetworkAvailable(Context c) {
        ConnectivityManager cm =
                (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
    }

    /**
     * This is intended for threads to show toast messages.
     * @param context
     * @param toastMsg
     */
    public static void showToast(final Context context, final String toastMsg) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    public static Calendar getNewYorkCalendarInstance(){
        return Calendar.getInstance(TimeZone.getTimeZone(YahooFinance.TIMEZONE));
    }

    public static Calendar calendarTimeReset(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar;
    }


    /**
     * Used to determine is a symbol already exists in the database
     * @param symbol The symbol to look up
     * @param cr The ContentResolver to access your ContentProvider
     * @return true if exists, otherwise false
     */
    public static boolean isEntryExist(String symbol, ContentResolver cr){
        Cursor cursor = null;
        try{
            cursor = cr.query(StockEntry.buildUri(symbol), null, null, null, null);
            if(cursor != null){
                return cursor.moveToFirst();
            }

        }finally {
            if(cursor != null){
                cursor.close();
            }
        }
        return false;
    }

    /**
     * Checks if the current time is between trading hours, regardless if stock market is closed or
     * not.
     * @return
     */
    public static boolean isDuringTradingHours(){
        //9:30am
        Calendar stockMarketOpen = getNewYorkCalendarInstance();
        stockMarketOpen.set(Calendar.HOUR_OF_DAY, STOCK_MARKET_OPEN_HOUR);
        stockMarketOpen.set(Calendar.MINUTE, STOCK_MARKET_OPEN_MINUTE);
        stockMarketOpen.set(Calendar.MILLISECOND, 0);

        //4:30pm
        Calendar stockMarketClose = getNewYorkCalendarInstance();
        stockMarketClose.set(Calendar.HOUR_OF_DAY, STOCK_MARKET_UPDATE_HOUR);
        stockMarketClose.set(Calendar.MINUTE, STOCK_MARKET_UPDATE_MINUTE);
        stockMarketClose.set(Calendar.MILLISECOND, 0);

        Calendar nowTime = getNewYorkCalendarInstance();

        // If nowTime is between 9:30am EST and 4:30 pm EST
        // assume it is trading hours
        if(!nowTime.before(stockMarketOpen) && nowTime.before(stockMarketClose)){
            return true;
        }

        return false;
    }

    /**
     * Checks to see if the stock list is up to date, if not then update
     * @param cr ContentResolver to query db for the previous update time
     * @return true if list can be updated, else false
     */
    public static boolean canUpdateList(ContentResolver cr){
        final String[] updateTimeProjection = new String[]{UpdateDateEntry.COLUMN_TIME_IN_MILLI};
        final int indexTimeInMilli = 0;
        Cursor cursor = null;
        try{
            cursor = cr.query(UpdateDateEntry.CONTENT_URI, updateTimeProjection, null, null, null);

            if(cursor != null){
                //Update Time doesn't exist yet so update
                if(!cursor.moveToFirst()){
                    return true;
                }

                Calendar nowTime = getNewYorkCalendarInstance();
                Calendar fourThirtyTime = getNewYorkCalendarInstance();
                fourThirtyTime.set(Calendar.HOUR_OF_DAY, STOCK_MARKET_UPDATE_HOUR);
                fourThirtyTime.set(Calendar.MINUTE, STOCK_MARKET_UPDATE_MINUTE);
                fourThirtyTime.set(Calendar.MILLISECOND, 0);

                Calendar lastUpdateTime  = Calendar.getInstance();
                long lastUpdateTimeInMilli  = cursor.getLong(indexTimeInMilli);
                lastUpdateTime.setTimeInMillis(lastUpdateTimeInMilli);

                int dayOfWeek = nowTime.get(Calendar.DAY_OF_WEEK);

                // ALGORITHM:
                // If nowTime is sunday or monday && < 4:30pm EST,
                // check if lastUpdateTime was before last Friday @ 4:30pm EST, if so update.
                // If nowTime < 4:30pm EST,
                // check if lastUpdateTime was before yesterday @ 4:30pm EST, if so update.
                // If nowTime >= 4:30pm EST,
                // check if lastUpdateTime was before today @ 4:30pmEST, if so update.
                // If nowTime is saturday && >= 4:30pm EST
                // check if lastUpdateTime was before last Friday @ 4:30pmEST, if so update.
                if(nowTime.before(fourThirtyTime)) {
                    if ((dayOfWeek == Calendar.SUNDAY)) {
                        // 2 days ago from Sunday is last Friday @ 4:30pm EST
                        fourThirtyTime.add(Calendar.DAY_OF_MONTH, -2);
                    } else if (dayOfWeek == Calendar.MONDAY) {
                        // 3 days ago from Monday is last Friday @ 4:30pm EST
                        fourThirtyTime.add(Calendar.DAY_OF_MONTH, -3);
                    } else{
                        // 1 day ago is yesterday @ 4:30pm EST
                        fourThirtyTime.add(Calendar.DAY_OF_MONTH, -1);
                    }
                } else {
                    if ((dayOfWeek == Calendar.SATURDAY)) {
                        // 1 days ago from Saturday is last Friday @ 4:30pm EST
                        fourThirtyTime.add(Calendar.DAY_OF_MONTH, -1);
                    }
                }

                //if lastUpdateTime is before the recentClose time, then update.
                if(lastUpdateTime.before(fourThirtyTime)){
                    return true;
                }
            }
        }finally {
            if(cursor != null){
                cursor.close();
            }
        }

        return false;
    }
}