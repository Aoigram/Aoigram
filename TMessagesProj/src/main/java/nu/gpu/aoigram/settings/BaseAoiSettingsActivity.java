package nu.gpu.aoigram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

import nu.gpu.aoigram.config.BoolConfig;
import nu.gpu.aoigram.config.ConfigItem;
import nu.gpu.aoigram.config.StringConfig;

public abstract class BaseAoiSettingsActivity extends BaseFragment {

    protected SizeNotifierFrameLayout contentView;
    protected UniversalRecyclerView listView;

    private String initialItemKey;
    private final SparseBooleanArray expandedGroups = new SparseBooleanArray();

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        contentView = new SizeNotifierFrameLayout(context) {
            @Override
            public void updateColors() {
                super.updateColors();
                BaseAoiSettingsActivity.this.updateColors();
            }
        };

        simpleActionBar.setBackButtonImage(parentLayout.isRightLayout() ? R.drawable.ic_ab_close : R.drawable.ic_ab_back);
        simpleActionBar.setTitle(getActionBarTitle());
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1 && onBackPressed(true)) {
                    finishFragment();
                }
            }
        });

        onCreateSettingsView(context);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, this::onItemLongClick);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (listView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(fragmentView);
                }
            }
        });
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        applyInitialItem();
        updateColors();

        return fragmentView = contentView;
    }

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) {
            listView.adapter.update(false);
        }
    }

    protected abstract CharSequence getActionBarTitle();

    protected abstract void fillItems(ArrayList<UItem> items, UniversalAdapter adapter);

    protected UItem asConfigCheck(BoolConfig config, @StringRes int titleResId) {
        UItem item = UItem.asCheck(getString(titleResId)).setChecked(config.get());
        item.object = config;
        return item;
    }

    protected UItem asConfigCheck(BoolConfig config, @StringRes int titleResId, @StringRes int detailsResId) {
        UItem item = asConfigCheck(config, titleResId);
        SpannableString arrow = new SpannableString("→");
        arrow.setSpan(new ColoredImageSpan(R.drawable.search_arrow, ColoredImageSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        item.subtext = AndroidUtilities.replaceMultipleCharSequence("→", getString(detailsResId), arrow);
        return item;
    }

    protected void addGroup(ArrayList<UItem> items, UniversalAdapter adapter, int id, int iconResId, @StringRes int titleResId, Option[] options) {
        int enabled = 0;
        for (Option option : options) {
            if (option.config.get()) {
                enabled++;
            }
        }
        boolean expanded = expandedGroups.get(id);
        UItem group = ExpandableSettingsCell.Factory.group(id, iconResId, getString(titleResId), enabled, options.length, expanded, view -> {
            expandedGroups.put(id, !expandedGroups.get(id));
            listView.adapter.update(true);
        });
        group.object = options;
        items.add(group);
        if (adapter == null || expanded) {
            for (Option option : options) {
                UItem item = ExpandableSettingsCell.Factory.option(getString(option.titleResId), option.config.get());
                item.object = option.config;
                items.add(item);
            }
        }
    }

    protected EditTextBoldCursor input(Context context, String value, String hint) {
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        input.setCursorColor(getThemedColor(Theme.key_dialogTextBlack));
        input.setBackground(Theme.createEditTextDrawable(context, true));
        input.setText(value);
        input.setHint(hint);
        input.setSelectAllOnFocus(true);
        return input;
    }

    protected void editString(CharSequence title, StringConfig config, String hint) {
        EditTextBoldCursor input = input(getContext(), config.get(), hint);
        LinearLayout layout = new LinearLayout(getContext());
        layout.addView(input, LayoutHelper.createLinear(-1, 48, 24, 0, 24, 0));
        AlertDialog dialog = new AlertDialog.Builder(getContext(), getResourceProvider()).setTitle(title).setView(layout)
                .setNegativeButton(getString(R.string.Cancel), null).setPositiveButton(getString(R.string.Save), (d, which) -> {
                    config.set(input.getText().toString().trim());
                    listView.adapter.update(true);
                })
                .create();
        Runnable showKeyboard = () -> {
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        };
        dialog.setOnShowListener(d -> AndroidUtilities.runOnUIThread(showKeyboard, 200));
        showDialog(dialog, d -> AndroidUtilities.cancelRunOnUIThread(showKeyboard));
    }

    protected void onItemClick(UItem item, View view, int position, float x, float y) {
        if (ExpandableSettingsCell.isGroup(item)) {
            if (LocaleController.isRTL ? x > dp(75) : x < view.getWidth() - dp(75)) {
                item.clickCallback.onClick(view);
                return;
            }
            for (Option option : (Option[]) item.object) {
                if (option.config.get() == item.checked) {
                    option.config.set(!item.checked);
                    onConfigToggled(option.config);
                }
            }
            listView.adapter.update(true);
        } else if (item.object instanceof BoolConfig config) {
            config.toggle();
            listView.adapter.update(true);
            onConfigToggled(config);
        }
    }

    protected void onConfigToggled(BoolConfig config) {
    }

    protected boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        String pageKey;
        String itemKey;
        if (item.object instanceof AoiSettingsSearchItem result) {
            pageKey = result.pageKey;
            itemKey = result.itemKey;
        } else {
            pageKey = getSettingsPageKey();
            itemKey = getItemKey(item);
            if (TextUtils.isEmpty(pageKey) || TextUtils.isEmpty(itemKey)) {
                return false;
            }
        }
        String deepLink = AoiSettingsDeepLink.create(pageKey, itemKey);

        ItemOptions.makeOptions(this, view)
                .setLongPressSelectionEnabled(false)
                .add(R.drawable.msg_link2, getString(R.string.CopyLink), () -> {
                    AndroidUtilities.addToClipboard(deepLink);
                    BulletinFactory.of(this).createCopyLinkBulletin().show();
                })
                .setScrimViewBackground(((RecyclerListView) view.getParent()).getClipBackground(view))
                .show();
        return true;
    }

    @Nullable
    protected String getSettingsPageKey() {
        return null;
    }

    @Nullable
    protected String getItemKey(UItem item) {
        return item.object instanceof ConfigItem<?> config ? config.getKey() : null;
    }

    protected void onCreateSettingsView(Context context) {
    }

    protected void updateCustomColors() {
    }

    @Nullable
    public BaseAoiSettingsActivity setInitialItemKey(@Nullable String itemKey) {
        initialItemKey = itemKey;
        boolean found = TextUtils.isEmpty(itemKey);
        ArrayList<UItem> items = new ArrayList<>();
        fillItems(items, null);
        for (UItem item : items) {
            found = found || itemKey.equals(getItemKey(item));
            if (ExpandableSettingsCell.isGroup(item)) {
                for (Option option : (Option[]) item.object) {
                    if (option.config.getKey().equals(itemKey)) {
                        expandedGroups.put(item.id, true);
                    }
                }
            }
        }
        return found ? this : null;
    }

    private void applyInitialItem() {
        if (TextUtils.isEmpty(initialItemKey)) {
            return;
        }
        for (int position = 0; position < listView.adapter.getItemCount(); position++) {
            UItem item = listView.adapter.getItem(position);
            if (item != null && initialItemKey.equals(getItemKey(item))) {
                int finalPosition = position;
                listView.layoutManager.scrollToPositionWithOffset(finalPosition, dp(12));
                listView.highlightRow(() -> finalPosition);
                return;
            }
        }
    }

    private void updateColors() {
        if (contentView == null) {
            return;
        }
        updateCustomColors();
        listView.invalidate();
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    protected record Option(BoolConfig config, @StringRes int titleResId) {}
}
