package de.mchllngr.quickopen.service;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.widget.Toast;

import com.f2prateek.rx.preferences.Preference;
import com.f2prateek.rx.preferences.RxSharedPreferences;
import com.google.gson.Gson;

import java.util.List;

import de.mchllngr.quickopen.R;
import de.mchllngr.quickopen.model.ApplicationModel;
import de.mchllngr.quickopen.util.CustomNotificationHelper;
import de.mchllngr.quickopen.util.GsonPreferenceAdapter;
import rx.functions.Action1;

/**
 * Service for handling the notification
 *
 * @author Michael Langer (<a href="https://github.com/mchllngr" target="_blank">GitHub</a>)
 */
public class NotificationService extends Service {

    /**
     * Name of an intentFilter used to notify the
     * {@link de.mchllngr.quickopen.receiver.NotificationServiceRestarter}.
     *
     * @see de.mchllngr.quickopen.receiver.NotificationServiceRestarter
     */
    private static final String INTENT_FILTER_NAME = "de.mchllngr.quickopen.service.RestartService";

    /**
     * Determines if the notification is enabled and should be shown.
     */
    private boolean notificationEnabled = true;
    /**
     * Determines if the current instance of the service should try to send a message to
     * {@link de.mchllngr.quickopen.receiver.NotificationServiceRestarter} if its about to be
     * destroyed for restarting the service.
     */
    private boolean shouldRestartAfterDestroy = true;
    /**
     * {@link CustomNotificationHelper}-reference for easier handling of the custom notification.
     */
    private CustomNotificationHelper customNotificationHelper;
    /**
     * {@link Preference}-reference for easier usage of the saved value for notificationEnabled
     * in the {@link RxSharedPreferences}.
     */
    private Preference<Boolean> notificationEnabledPref;
    /**
     * {@link Preference}-reference for easier usage of the saved value for notificationVisibility
     * in the {@link RxSharedPreferences}.
     */
    private Preference<String> notificationVisibilityPref;
    /**
     * {@link Preference}-reference for easier usage of the saved value for notificationPriority
     * in the {@link RxSharedPreferences}.
     */
    private Preference<String> notificationPriorityPref;
    /**
     * {@link Preference}-reference for easier usage of the saved value for packageNames in the
     * {@link RxSharedPreferences}.
     */
    private Preference<List> packageNamesPref;

    @Override
    public void onCreate() {
        super.onCreate();

        int notificationIcon = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ?
                R.drawable.ic_speaker_notes_white_24px : R.drawable.ic_notification;

        customNotificationHelper = new CustomNotificationHelper(
                this, getResources().getInteger(R.integer.notification_id),
                notificationIcon
        );

        RxSharedPreferences rxSharedPreferences = RxSharedPreferences.create(
                PreferenceManager.getDefaultSharedPreferences(this)
        );

        notificationEnabledPref = rxSharedPreferences.getBoolean(
                getString(R.string.pref_notification_enabled),
                Boolean.parseBoolean(getString(R.string.pref_notification_enabled_default_value))
        );

        notificationVisibilityPref = rxSharedPreferences.getString(
                getString(R.string.pref_notification_visibility),
                getString(R.string.pref_notification_visibility_option_private)
        );

        notificationPriorityPref = rxSharedPreferences.getString(
                getString(R.string.pref_notification_priority),
                getString(R.string.pref_notification_priority_option_default)
        );

        GsonPreferenceAdapter<List> adapter = new GsonPreferenceAdapter<>(new Gson(), List.class);
        packageNamesPref = rxSharedPreferences.getObject(
                getString(R.string.pref_package_names),
                null,
                adapter
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        initService();

        return START_STICKY;
    }

    /**
     * Initialises the {@link Service} and used variables.
     */
    private void initService() {
        if (customNotificationHelper == null ||
                notificationVisibilityPref == null ||
                notificationPriorityPref == null ||
                packageNamesPref == null)
            onError();

        // subscribe to changes in notificationEnabledPref
        notificationEnabledPref.asObservable().subscribe(new Action1<Boolean>() {
            @Override
            public void call(Boolean notificationEnabled) {
                NotificationService.this.notificationEnabled = notificationEnabled;

                if (notificationEnabled) {
                    ApplicationModel[] applicationModels = ApplicationModel
                            .prepareApplicationModelsArray(
                                    NotificationService.this,
                                    packageNamesPref.get()
                            );
                    showNotification(applicationModels);
                } else
                    hideNotification();
            }
        });

        // subscribe to changes in notificationVisibilityPref
        notificationVisibilityPref.asObservable().subscribe(new Action1<String>() {
            @Override
            public void call(String notificationVisibility) {
                if (TextUtils.isEmpty(notificationVisibility)) return;

                int notificationVisibilityValue = NotificationCompat.VISIBILITY_PUBLIC;
                if (notificationVisibility.equals(getString(
                        R.string.pref_notification_visibility_value_private)))
                    notificationVisibilityValue = NotificationCompat.VISIBILITY_PRIVATE;
                else if (notificationVisibility.equals(getString(
                        R.string.pref_notification_visibility_value_secret)))
                    notificationVisibilityValue = NotificationCompat.VISIBILITY_SECRET;

                customNotificationHelper.setNotificationVisibility(
                        notificationVisibilityValue,
                        notificationEnabled
                );
            }
        });

        // subscribe to changes in notificationPriorityPref
        notificationPriorityPref.asObservable().subscribe(new Action1<String>() {
            @Override
            public void call(String notificationPriority) {
                if (TextUtils.isEmpty(notificationPriority)) return;

                int notificationPriorityValue = Notification.PRIORITY_MAX;
                if (notificationPriority.equals(getString(
                        R.string.pref_notification_priority_value_high)))
                    notificationPriorityValue = Notification.PRIORITY_HIGH;
                else if (notificationPriority.equals(getString(
                        R.string.pref_notification_priority_value_default)))
                    notificationPriorityValue = Notification.PRIORITY_DEFAULT;
                else if (notificationPriority.equals(getString(
                        R.string.pref_notification_priority_value_low)))
                    notificationPriorityValue = Notification.PRIORITY_LOW;
                else if (notificationPriority.equals(getString(
                        R.string.pref_notification_priority_value_min)))
                    notificationPriorityValue = Notification.PRIORITY_MIN;

                customNotificationHelper.setNotificationPriority(
                        notificationPriorityValue,
                        notificationEnabled
                );
            }
        });

        // subscribe to changes in packageNamesPref
        packageNamesPref.asObservable().subscribe(new Action1<List>() {
            @Override
            public void call(List list) {
                ApplicationModel[] applicationModels = ApplicationModel
                        .prepareApplicationModelsArray(
                                NotificationService.this,
                                list
                        );

                if (applicationModels.length == 0)
                    hideNotification();
                else
                    showNotification(applicationModels);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        hideNotification();

        if (shouldRestartAfterDestroy) {
            // sends broadcast when service is about to be destroyed
            Intent broadcastIntent = new Intent(INTENT_FILTER_NAME);
            sendBroadcast(broadcastIntent);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Calls {@link CustomNotificationHelper} to show the notification with given array of
     * {@link ApplicationModel}s.
     *
     * @param applicationModels array of {@link ApplicationModel}s to show in notification
     */
    private void showNotification(ApplicationModel... applicationModels) {
        if (notificationEnabled && customNotificationHelper != null)
            customNotificationHelper.showCustomNotification(applicationModels);
    }

    /**
     * Calls {@link CustomNotificationHelper} to hide the notification.
     */
    private void hideNotification() {
        if (customNotificationHelper != null)
            customNotificationHelper.hideNotification();
    }

    /**
     * Shows an error-message, stops the {@link Service} from restarting by setting
     * {@code shouldRestartAfterDestroy} and stops the {@link Service}.
     */
    private void onError() {
        showErrorMessage();
        shouldRestartAfterDestroy = false;
        stopSelf();
    }

    /**
     * Shows the error-message.
     */
    private void showErrorMessage() {
        // TODO replace Toast with Error-Notification (click starts activity)
        Toast.makeText(this, getString(R.string.notification_service_error),
                Toast.LENGTH_SHORT).show();
    }
}