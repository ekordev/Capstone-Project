package com.mcochin.stockstreaks;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

/**
 * Created by Marco on 2/10/2016.
 */
public class SplashActivity extends AppCompatActivity{
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d("MainAct", "onCreate Splash");
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
