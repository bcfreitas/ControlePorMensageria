package com.bcfreitas.controlepormensageria;

import android.app.Application;
import android.content.Context;

import com.secneo.sdk.Helper;

public class DjiMobileApp extends Application {

    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext(paramContext);
        Helper.install(DjiMobileApp.this);
    }

}


