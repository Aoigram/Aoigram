package nu.gpu.aoigram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.view.View;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import nu.gpu.aoigram.utils.CrashLogs;
import nu.gpu.aoigram.utils.ErrorFeedback;

public final class AoiCrashLogsActivity extends BaseAoiSettingsActivity {

    private static final int CLEAR_ROW = 1;

    private StickerEmptyView emptyView;

    @Override
    public boolean onFragmentCreate() {
        CrashLogs.clearPending();
        getNotificationCenter().postNotificationName(NotificationCenter.newSuggestionsAvailable);
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        emptyView = new StickerEmptyView(context, null, StickerEmptyView.STICKER_TYPE_SEARCH, getResourceProvider());
        emptyView.title.setText(getString(R.string.NoCrashLogs));
        emptyView.subtitle.setText(getString(R.string.NoCrashLogsInfo));
        emptyView.showProgress(false, false);
        emptyView.setPadding(0, mSystemInsets.top + ActionBar.getCurrentActionBarHeight(), 0, mSystemInsets.bottom);
        contentView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setEmptyView(emptyView);
        return view;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        emptyView.setPadding(0, top + ActionBar.getCurrentActionBarHeight(), 0, bottom);
    }

    @Override
    protected CharSequence getActionBarTitle() {
        return getString(R.string.CrashLogs);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        File[] logs = CrashLogs.list();
        if (logs.length == 0) {
            return;
        }
        for (File log : logs) {
            UItem item = UItem.asButton(0, CrashLogs.getType(log), LocaleController.getInstance().getFormatterStats().format(CrashLogs.getTime(log)));
            item.object = log;
            items.add(item);
        }
        items.add(UItem.asShadow(getString(R.string.CrashLogsInfo)));
        items.add(UItem.asButton(CLEAR_ROW, getString(R.string.ClearCrashLogs)).red());
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof File log) {
            showLog(log);
        } else if (item.id == CLEAR_ROW) {
            CrashLogs.clear();
            listView.adapter.update(true);
        }
    }

    private void showLog(File log) {
        String report;
        try {
            report = CrashLogs.read(log);
        } catch (IOException e) {
            FileLog.e(e);
            ErrorFeedback.show(getString(R.string.ErrorOccurred), BulletinFactory.of(this), this::showDialog, true);
            return;
        }
        SpannableString text = new SpannableString(report);
        text.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO), dp(12)), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(CrashLogs.getType(log))
                .setMessage(text)
                .setNeutralButton(getString(R.string.Copy), (d, which) -> {
                    if (AndroidUtilities.addToClipboard(report) && AndroidUtilities.shouldShowClipboardToast()) {
                        BulletinFactory.of(this).createCopyBulletin(getString(R.string.TextCopied)).show();
                    }
                })
                .setNegativeButton(getString(R.string.Close), null)
                .setPositiveButton(getString(R.string.ShareFile), (d, which) -> share(log))
                .create();
        showDialog(dialog);
        dialog.getMessageTextView().setTextIsSelectable(true);
    }

    private void share(File log) {
        Uri uri = FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", log);
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        getParentActivity().startActivity(Intent.createChooser(intent, getString(R.string.ShareFile)));
    }
}
