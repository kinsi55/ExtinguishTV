package com.github.kinsi55.extinguishtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Phone UI: connects to a Fire TV's ADB over the network and enables/disables the apps in {@link Catalog}. */
public class MainActivity extends Activity {
    private static final int ACCENT = 0xFFFFD27A;
    private static final int MUTED = 0xFFA0A8B8;
    private static final int ADB_PORT = 5555;
    private static final int REBOOT_DELAY_S = 5;

    /** One catalog (or unknown) entry as found on the TV; box is null for locked and missing entries. */
    private static final class Row {
        final Catalog.Entry entry;
        final List<String> installed = new ArrayList<>();
        String lockReason;
        boolean missing;
        boolean unknown;
        CheckBox box;
        TextView label;

        Row(Catalog.Entry entry) {
            this.entry = entry;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private TvState tv;
    private SharedPreferences prefs;
    private EditText ipField;
    private TextView status;
    private Button connectButton;
    private Button applyButton;
    private Button recommendedButton;
    private LinearLayout list;
    private volatile boolean busy;
    private boolean unknownWarningShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("selection", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121722);
        root.setPadding(dp(16), dp(24), dp(16), dp(8));
        root.setFitsSystemWindows(true);

        root.addView(text("ExtinguishTV", 24, Color.WHITE));
        root.addView(text("Needs ADB Debugging on the TV (Settings → My Fire TV → Developer Options). "
                + "Checked = disabled. Nothing is uninstalled; uncheck and apply to bring an app back.", 13, MUTED));

        LinearLayout connectRow = new LinearLayout(this);
        connectRow.setGravity(Gravity.CENTER_VERTICAL);
        connectRow.setPadding(0, dp(8), 0, 0);
        ipField = new EditText(this);
        ipField.setHint("TV IP address");
        ipField.setText(prefs.getString("ip", ""));
        ipField.setTextColor(Color.WHITE);
        ipField.setHintTextColor(MUTED);
        ipField.setSingleLine(true);
        ipField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        ipField.setImeOptions(EditorInfo.IME_ACTION_GO);
        ipField.setOnEditorActionListener((v, actionId, event) -> {
            connect();
            return true;
        });
        connectRow.addView(ipField, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        connectButton = new Button(this);
        connectButton.setText("Connect");
        connectButton.setOnClickListener(v -> connect());
        connectRow.addView(connectButton);
        root.addView(connectRow);

        status = text("", 14, ACCENT);
        status.setPadding(0, dp(6), 0, dp(6));
        root.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        applyButton = new Button(this);
        applyButton.setText("Apply & reboot");
        applyButton.setOnClickListener(v -> apply());
        recommendedButton = new Button(this);
        recommendedButton.setText("Disable recommended");
        recommendedButton.setOnClickListener(v -> disableRecommended());
        buttons.addView(applyButton);
        buttons.addView(recommendedButton);
        root.addView(buttons);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        setButtonsEnabled();
        if (ipField.length() > 0) connect();
    }

    // --- Actions -----------------------------------------------------------------------------

    private void connect() {
        final String host = ipField.getText().toString().trim();
        if (host.isEmpty() || busy) return;
        prefs.edit().putString("ip", host).apply();
        startBusy();
        new Thread(() -> {
            try (AdbClient adb = open(host)) {
                postStatus("Reading app states from the TV…");
                TvState state = TvState.read(adb);
                runOnUiThread(() -> {
                    tv = state;
                    buildRows();
                    setStatus("Connected to " + host + ".");
                });
            } catch (Exception e) {
                postStatus(describe(e));
            } finally {
                runOnUiThread(this::finishBusy);
            }
        }).start();
    }

    private void apply() {
        if (busy || tv == null) return;

        final List<String> toDisable = new ArrayList<>();
        final List<String> toEnable = new ArrayList<>();
        final List<String> unknownToDisable = new ArrayList<>();
        for (Row row : rows) {
            if (row.box == null) continue;
            boolean wantDisabled = row.box.isChecked();
            for (String pkg : row.installed) {
                if (tv.disabled.contains(pkg) == wantDisabled) continue;
                (wantDisabled ? toDisable : toEnable).add(pkg);
                if (wantDisabled && row.unknown) unknownToDisable.add(pkg);
            }
        }

        if (toDisable.isEmpty() && toEnable.isEmpty()) {
            setStatus("Nothing to change: every app already matches its checkbox.");
            return;
        }
        if (unknownToDisable.isEmpty()) {
            run(toDisable, toEnable);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Disable unknown apps?")
                .setMessage("These aren't in the catalog, so nobody has checked what they do:\n\n"
                        + TextUtils.join("\n", unknownToDisable)
                        + "\n\nIf one of them breaks the TV's network connection, this app can't reach the TV to "
                        + "undo it, and recovering may need a factory reset.")
                .setPositiveButton("Disable anyway", (d, w) -> run(toDisable, toEnable))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void run(final List<String> toDisable, final List<String> toEnable) {
        final String host = prefs.getString("ip", "");
        SharedPreferences.Editor editor = prefs.edit();
        for (Row row : rows) {
            if (row.box != null) editor.putBoolean(key(row.entry), row.box.isChecked());
        }
        editor.apply();

        startBusy();
        new Thread(() -> {
            try (AdbClient adb = open(host)) {
                for (String pkg : toDisable) {
                    postStatus("Disabling " + pkg + "…");
                    adb.run("shell:pm disable-user --user 0 " + pkg);
                }
                for (String pkg : toEnable) {
                    postStatus("Enabling " + pkg + "…");
                    adb.run("shell:pm enable " + pkg);
                }

                TvState after = TvState.read(adb);
                List<String> failures = new ArrayList<>();
                for (String pkg : toDisable) if (!after.disabled.contains(pkg)) failures.add("still enabled: " + pkg);
                for (String pkg : toEnable) if (after.disabled.contains(pkg)) failures.add("still disabled: " + pkg);
                runOnUiThread(() -> {
                    tv = after;
                    refreshLabels();
                });

                if (!failures.isEmpty()) {
                    postStatus("Some changes failed, so the TV was not rebooted:\n" + String.join("\n", failures));
                    return;
                }
                for (int i = REBOOT_DELAY_S; i > 0; i--) {
                    postStatus("Applied " + (toDisable.size() + toEnable.size()) + " change(s). Rebooting TV in " + i + "…");
                    Thread.sleep(1000);
                }
                adb.run("reboot:");
                postStatus("Applied " + (toDisable.size() + toEnable.size()) + " change(s). The TV is rebooting.");
            } catch (Exception e) {
                postStatus(describe(e));
            } finally {
                runOnUiThread(this::finishBusy);
            }
        }).start();
    }

    /** Ticks every recommended (privacy) entry, leaves all other checkboxes as they are, and applies. */
    private void disableRecommended() {
        if (busy || tv == null) return;
        for (Row row : rows) {
            if (row.box != null && row.entry.recommended) row.box.setChecked(true);
        }
        apply();
    }

    // --- TV access ---------------------------------------------------------------------------

    private AdbClient open(String host) throws Exception {
        postStatus("Connecting to " + host + "… If the TV asks \"Allow USB debugging?\", tick \"Always allow\" and choose OK.");
        String keyName = "extinguishtv@" + Build.MODEL.replaceAll("[^A-Za-z0-9._-]", "_");
        return new AdbClient(host, ADB_PORT, AdbClient.loadOrCreateKeys(getFilesDir()), keyName, 120_000);
    }

    // --- List UI -----------------------------------------------------------------------------

    private void buildRows() {
        rows.clear();
        list.removeAllViews();
        List<Row> known = new ArrayList<>();
        List<Row> unknown = new ArrayList<>();
        List<Row> missing = new ArrayList<>();
        List<Row> locked = new ArrayList<>();

        Set<String> catalogued = new HashSet<>();
        for (Catalog.Entry entry : Catalog.ENTRIES) {
            catalogued.addAll(entry.packages);
            Row row = new Row(entry);
            for (String pkg : entry.packages) {
                if (tv.installed.contains(pkg)) row.installed.add(pkg);
            }
            if (row.installed.isEmpty()) {
                row.missing = true;
                missing.add(row);
                continue;
            }
            row.lockReason = lockReason(row);
            (row.lockReason != null ? locked : known).add(row);
        }

        List<String> others = new ArrayList<>();
        for (String pkg : tv.installed) {
            if (!catalogued.contains(pkg) && Catalog.isVendorPackage(pkg)) others.add(pkg);
        }
        Collections.sort(others);
        for (String pkg : others) {
            Row row = new Row(new Catalog.Entry(pkg, "Not in the catalog; purpose unknown.", false, null, pkg));
            row.installed.add(pkg);
            row.unknown = true;
            row.lockReason = lockReason(row);
            (row.lockReason != null ? locked : unknown).add(row);
        }

        addSection("Known apps", "★ = recommended to disable for privacy (\"Disable recommended\").", known);
        addSection("Unknown Amazon apps", "Not in the catalog, so there's no description. Untouched unless you tick them; "
                + "if one breaks something, untick it and apply again.", unknown);
        addSection("Missing / not found", "Listed in the catalog but not installed on this TV, e.g. removed by an update.", missing);
        addSection("Can't be disabled", null, locked);
        refreshLabels();
        setButtonsEnabled();
    }

    /** Explains the risk the first time an unknown app is ticked; the confirmation before applying still follows. */
    private void warnAboutUnknownOnce(CheckBox box) {
        if (unknownWarningShown) return;
        unknownWarningShown = true;
        new AlertDialog.Builder(this)
                .setTitle("Unknown app")
                .setMessage("Apps in this section aren't in the catalog, so nobody has checked what they do.\n\n"
                        + "If one of them breaks the TV's network connection, this app can't reach the TV to undo it, "
                        + "and recovering may need a factory reset.\n\n"
                        + "You won't be warned again for other unknown apps until the app restarts, "
                        + "but you'll be asked to confirm before anything is applied.")
                .setPositiveButton("Keep ticked", null)
                .setNegativeButton("Untick", (d, w) -> box.setChecked(false))
                .show();
    }

    private String lockReason(Row row) {
        if (row.entry.lockReason != null) return row.entry.lockReason;
        for (String pkg : row.installed) {
            if (tv.persistent.contains(pkg)) return "Always-running system app; disabling it could crash-loop.";
            if (Catalog.PROTECTED.contains(pkg)) return "Protected by Fire OS.";
        }
        return null;
    }

    private void addSection(String title, String note, List<Row> section) {
        if (section.isEmpty()) return;
        TextView header = text(title + " (" + section.size() + ")", 17, Color.WHITE);
        header.setPadding(0, dp(16), 0, dp(4));
        list.addView(header);
        if (note != null) list.addView(text(note, 13, MUTED));

        for (Row row : section) {
            if (row.missing || row.lockReason != null) {
                row.label = text("", 15, MUTED);
                row.label.setPadding(dp(8), dp(6), dp(8), dp(6));
                list.addView(row.label);
            } else {
                row.box = new CheckBox(this);
                row.box.setTextColor(Color.WHITE);
                row.box.setTextSize(15);
                row.box.setPadding(dp(8), dp(6), dp(8), dp(6));
                // Last applied choice if there is one, otherwise whatever the TV has now: nothing changes by default.
                String key = key(row.entry);
                row.box.setChecked(prefs.contains(key) ? prefs.getBoolean(key, false) : currentState(row).equals("disabled"));
                row.box.setOnCheckedChangeListener((b, checked) -> {
                    refreshLabels();
                    if (checked && row.unknown) warnAboutUnknownOnce(row.box);
                });
                list.addView(row.box);
            }
            rows.add(row);
        }
    }

    private void refreshLabels() {
        for (Row row : rows) {
            SpannableStringBuilder s = new SpannableStringBuilder(row.entry.recommended ? "★ " + row.entry.name : row.entry.name);
            if (row.missing) {
                appendStyled(s, "   not installed", MUTED);
                appendStyled(s, "\n" + (row.entry.description != null ? row.entry.description : row.entry.lockReason)
                        + "\n" + TextUtils.join(", ", row.entry.packages), MUTED);
                row.label.setText(s);
                continue;
            }
            String state = currentState(row);
            if (row.box != null) {
                boolean wantDisabled = row.box.isChecked();
                boolean pending = !state.equals(wantDisabled ? "disabled" : "enabled");
                appendStyled(s, "   now " + state + (pending ? (wantDisabled ? " → will disable" : " → will enable") : ""),
                        pending ? ACCENT : MUTED);
                appendStyled(s, "\n" + row.entry.description, MUTED);
                if (row.installed.size() < row.entry.packages.size()) {
                    List<String> gone = new ArrayList<>(row.entry.packages);
                    gone.removeAll(row.installed);
                    appendStyled(s, "\nNot installed: " + TextUtils.join(", ", gone), MUTED);
                }
                row.box.setText(s);
            } else {
                appendStyled(s, "   now " + state, MUTED);
                appendStyled(s, "\n" + row.lockReason, MUTED);
                row.label.setText(s);
            }
        }
    }

    private String currentState(Row row) {
        int disabled = 0;
        for (String pkg : row.installed) {
            if (tv.disabled.contains(pkg)) disabled++;
        }
        if (disabled == 0) return "enabled";
        if (disabled == row.installed.size()) return "disabled";
        return "partly disabled";
    }

    // --- Helpers -----------------------------------------------------------------------------

    private static String describe(Exception e) {
        if (e instanceof ConnectException || e instanceof NoRouteToHostException) {
            return "Couldn't reach the TV. Check the IP, that the phone is on the same network, and that ADB Debugging is on.";
        }
        if (e instanceof SocketTimeoutException) {
            return "No answer from the TV. If a debugging prompt is showing, choose OK (tick \"Always allow\") and try again.";
        }
        return "Failed: " + e;
    }

    private static String key(Catalog.Entry entry) {
        return "disable:" + entry.packages.get(0);
    }

    private void setStatus(String message) {
        status.setText(message);
    }

    private void postStatus(String message) {
        runOnUiThread(() -> setStatus(message));
    }

    private void startBusy() {
        busy = true;
        setButtonsEnabled();
    }

    private void finishBusy() {
        busy = false;
        setButtonsEnabled();
    }

    private void setButtonsEnabled() {
        connectButton.setEnabled(!busy);
        applyButton.setEnabled(!busy && tv != null);
        recommendedButton.setEnabled(!busy && tv != null);
    }

    private TextView text(String value, int sizeSp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        return t;
    }

    private static void appendStyled(SpannableStringBuilder s, String value, int color) {
        int start = s.length();
        s.append(value);
        s.setSpan(new ForegroundColorSpan(color), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new RelativeSizeSpan(0.85f), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
