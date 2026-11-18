package nu.gpu.aoigram.login;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SlideView;

@SuppressLint("ViewConstructor")
public class QrLoginView extends SlideView implements NotificationCenter.NotificationCenterDelegate {

    private static final int EXPORT_FLAGS = ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagTryDifferentDc | ConnectionsManager.RequestFlagEnableUnauthorized;
    private static final int IMPORT_FLAGS = ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin;

    private final BaseFragment fragment;
    private final Utilities.Callback<TLRPC.TL_auth_authorization> onAuthorized;
    private final Runnable onPasswordRequired;
    private final QrCodeView qrView;
    private final Runnable refresh = this::exportToken;

    private NotificationCenter.ObserversGroup observers;
    private boolean shown;
    private int requestId;

    public QrLoginView(Context context, BaseFragment fragment, Utilities.Callback<TLRPC.TL_auth_authorization> onAuthorized, Runnable onPasswordRequired) {
        super(context);
        this.fragment = fragment;
        this.onAuthorized = onAuthorized;
        this.onPasswordRequired = onPasswordRequired;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(0, 0, 0, dp(16));

        qrView = new QrCodeView(context);
        addView(qrView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setText(getString(R.string.QrLogin));
        titleView.setGravity(Gravity.CENTER);
        titleView.setLineSpacing(dp(2), 1.0f);
        ThemeBind.textColor(titleView, Theme.key_windowBackgroundWhiteBlackText);
        addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 28, 32, 0));

        String[] steps = {
                getString(R.string.QrLoginStep1),
                formatString(R.string.QrLoginStep2, getString(R.string.Settings), getString(R.string.Devices), getString(R.string.LinkDesktopDevice)),
                getString(R.string.QrLoginStep3)
        };
        for (int i = 0; i < steps.length; i++) {
            TextView numberView = createStepText(context, String.format(LocaleController.isRTL ? ".%d" : "%d.", i + 1));
            TextView stepView = createStepText(context, AndroidUtilities.replaceTags(steps[i]));
            LinearLayout row = new LinearLayout(context);
            if (LocaleController.isRTL) {
                row.addView(stepView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
                row.addView(numberView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));
            } else {
                row.addView(numberView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
                row.addView(stepView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            }
            addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 32, i == 0 ? 16 : 8, 32, 0));
        }
    }

    private static TextView createStepText(Context context, CharSequence text) {
        TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView.setText(text);
        textView.setLineSpacing(dp(2), 1.0f);
        ThemeBind.textColor(textView, Theme.key_windowBackgroundWhiteGrayText6);
        return textView;
    }

    @Override
    public String getHeaderName() {
        return getString(R.string.QrLogin);
    }

    @Override
    public boolean needBackButton() {
        return true;
    }

    @Override
    public boolean onBackPressed(boolean force) {
        stop();
        return true;
    }

    @Override
    public void onShow() {
        super.onShow();
        shown = true;
        observers = fragment.getNotificationCenter().createObserversGroup(this).add(NotificationCenter.loginTokenUpdated);
        exportToken();
    }

    @Override
    public void onHide() {
        super.onHide();
        stop();
        qrView.clear();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (shown && requestId == 0) {
            exportToken();
        }
    }

    @Override
    public void onDestroyActivity() {
        super.onDestroyActivity();
        stop();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.loginTokenUpdated) {
            exportToken();
        }
    }

    private void stop() {
        shown = false;
        if (observers != null) {
            observers.removeAllObservers();
            observers = null;
        }
        AndroidUtilities.cancelRunOnUIThread(refresh);
        if (requestId != 0) {
            fragment.getConnectionsManager().cancelRequest(requestId, false);
            requestId = 0;
        }
    }

    private void exportToken() {
        AndroidUtilities.cancelRunOnUIThread(refresh);
        TLRPC.TL_auth_exportLoginToken req = new TLRPC.TL_auth_exportLoginToken();
        req.api_id = BuildVars.APP_ID;
        req.api_hash = BuildVars.APP_HASH;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig userConfig = UserConfig.getInstance(a);
            if (userConfig.isClientActivated()) {
                req.except_ids.add(userConfig.getClientUserId());
            }
        }
        send(req, EXPORT_FLAGS);
    }

    private void send(TLObject req, int flags) {
        requestId = fragment.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            requestId = 0;
            if (shown) {
                onResponse(response, error);
            }
        }), flags);
    }

    private void onResponse(TLObject response, TLRPC.TL_error error) {
        if (response instanceof TLRPC.TL_auth_loginToken token) {
            qrView.setLink("tg://login?token=" + Base64.encodeToString(token.token, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));
            AndroidUtilities.runOnUIThread(refresh, (token.expires - fragment.getConnectionsManager().getCurrentTime()) * 1000L);
        } else if (response instanceof TLRPC.TL_auth_loginTokenMigrateTo migrate) {
            fragment.getConnectionsManager().setDefaultDatacenterId(migrate.dc_id);
            TLRPC.TL_auth_importLoginToken req = new TLRPC.TL_auth_importLoginToken();
            req.token = migrate.token;
            send(req, IMPORT_FLAGS);
        } else if (response instanceof TLRPC.TL_auth_loginTokenSuccess success) {
            stop();
            onAuthorized.run((TLRPC.TL_auth_authorization) success.authorization);
        } else if (error != null) {
            stop();
            if (error.text.contains("SESSION_PASSWORD_NEEDED")) {
                onPasswordRequired.run();
            } else {
                BulletinFactory.of(fragment).showForError(error);
                fragment.onBackPressed(true);
            }
        }
    }
}
