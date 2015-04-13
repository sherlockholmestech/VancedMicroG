/*
 * Copyright 2013-2015 µg Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.microg.gms.checkin;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;

import com.google.android.gms.R;

import org.microg.gms.auth.AuthManager;
import org.microg.gms.common.Constants;
import org.microg.gms.common.DeviceConfiguration;
import org.microg.gms.common.DeviceIdentifier;
import org.microg.gms.common.PhoneInfo;
import org.microg.gms.common.Utils;
import org.microg.gms.gservices.GServices;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CheckinManager {
    private static final long MIN_CHECKIN_INTERVAL = 3 * 60 * 60 * 1000; // 3 hours

    public static synchronized LastCheckinInfo checkin(Context context, boolean force) throws IOException {
        LastCheckinInfo info = LastCheckinInfo.read(context);
        if (!force && info.lastCheckin > System.currentTimeMillis() - MIN_CHECKIN_INTERVAL)
            return null;
        List<CheckinClient.Account> accounts = new ArrayList<CheckinClient.Account>();
        AccountManager accountManager = AccountManager.get(context);
        String accountType = context.getString(R.string.google_account_type);
        for (Account account : accountManager.getAccountsByType(accountType)) {
            String token = new AuthManager(context, account.name, Constants.GMS_PACKAGE_NAME, "ac2dm").getAuthToken();
            accounts.add(new CheckinClient.Account(account.name, token));
        }
        CheckinRequest request =
                CheckinClient.makeRequest(Utils.getBuild(context), new DeviceConfiguration(context),
                        new DeviceIdentifier(), new PhoneInfo(), info, accounts); // TODO
        return handleResponse(context, CheckinClient.request(request));
    }

    private static LastCheckinInfo handleResponse(Context context, CheckinResponse response) {
        LastCheckinInfo info = new LastCheckinInfo();
        info.androidId = response.androidId;
        info.lastCheckin = response.timeMs;
        info.securityToken = response.securityToken;
        info.digest = response.digest;
        info.versionInfo = response.versionInfo;
        info.deviceDataVersionInfo = response.deviceDataVersionInfo;
        info.write(context);

        ContentResolver resolver = context.getContentResolver();
        for (CheckinResponse.GservicesSetting setting : response.setting) {
            GServices.setString(resolver, setting.name.utf8(), setting.value.utf8());
        }

        return info;
    }
}
