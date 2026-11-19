package com.github.kinsi55.extinguishtv;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The TV apps the UI knows about. Descriptions are inferred from each package's components and
 * permissions (not from its code), so treat them as best guesses.
 */
final class Catalog {
    static final class Entry {
        final String name;
        final String description;
        final List<String> packages;
        /** Disabled by "Disable recommended": clear privacy wins (telemetry, ads, tracking). */
        final boolean recommended;
        /** Non-null for apps that must not be touched; shown as info only. */
        final String lockReason;

        Entry(String name, String description, boolean recommended, String lockReason, String... packages) {
            this.name = name;
            this.description = description;
            this.recommended = recommended;
            this.lockReason = lockReason;
            this.packages = Arrays.asList(packages);
        }
    }

    private static Entry recommended(String name, String description, String... packages) {
        return new Entry(name, description, true, null, packages);
    }

    private static Entry optional(String name, String description, String... packages) {
        return new Entry(name, description, false, null, packages);
    }

    private static Entry locked(String name, String reason, String... packages) {
        return new Entry(name, null, false, reason, packages);
    }

    private static final String NETWORK_CRITICAL =
            "Network / remote connectivity. If disabling it cut the TV off the network, this app could no longer undo it.";

    static final List<Entry> ENTRIES = Arrays.asList(
            recommended("Amazon telemetry pipeline", "Collects and uploads usage events (ceviche).",
                    "com.amazon.ceviche"),
            recommended("Content recognition (ACR)", "Automatic content recognition (identifies what's on screen). "
                            + "May be installed as a stub that Amazon can upgrade later.",
                    "com.amazon.tv.acr"),
            recommended("Performance telemetry", "Collects performance stats at boot / screen-on and uploads them.",
                    "com.amazon.perfcollection", "com.amazon.perfc"),
            recommended("System & Wi-Fi metrics", "System and wireless telemetry.",
                    "com.amazon.tv.fw.metrics", "com.amazon.wirelessmetrics.service"),
            recommended("Remote support (Live Assist)", "Appears to let Amazon support view the screen and send remote "
                            + "input; woken by push messages.",
                    "com.amazon.ods.kindleconnect"),
            recommended("Wi-Fi password upload", "Appears to upload saved Wi-Fi passwords (and location) to the Amazon "
                            + "account. Already uploaded passwords stay there until deleted on amazon.com.",
                    "com.amazon.wifilocker"),
            recommended("Advertising ID service", "Hands out the advertising ID to apps.",
                    "com.amazon.hybridadidservice"),
            recommended("Developer data sharing", "Shares content-personalization data with app developers.",
                    "com.amazon.tv.developer.dataservice"),
            recommended("Blackbird ad engine", "Amazon ad sync, ad bidding and ad pages. Does not remove the "
                            + "home-screen banner ads.",
                    "com.amazon.kso.blackbird"),

            optional("Prime Video", "Prime Video app.",
                    "com.amazon.avod"),
            optional("Prime Video engine", "Background engine behind Prime Video (landing page, search, purchases).",
                    "com.amazon.firebat"),
            optional("Amazon Music", "Amazon Music app and its installer stub.",
                    "com.amazon.bueller.music", "com.amazon.dummy.music"),
            optional("Amazon Photos", "Amazon Photos app.",
                    "com.amazon.bueller.photos"),
            optional("Game hub", "Amazon's games storefront.",
                    "com.amazon.gamehub"),
            optional("Amazon Shopping", "Shopping on the TV.",
                    "com.amazon.shoptv.client"),
            optional("Amazon Kids", "Kids / FreeTime profiles.",
                    "com.amazon.tahoe"),
            optional("Alexa media player", "Plays music and podcasts requested through Alexa.",
                    "com.amazon.alexamediaplayer.runtime.ftv"),
            optional("Alexa visual & AI", "Alexa content renderer, APL renderer (also shows satisfaction surveys), "
                            + "on-device AI, voice features.",
                    "com.amazon.cardinal", "com.amazon.aria", "com.amazon.aiondec", "com.amazon.sneakpeek"),
            optional("Alexa calling & alerts", "Alexa calls / Drop In, alarms, notifications, media control.",
                    "com.amazon.rtcsessioncontroller", "com.amazon.comms.starktachyon", "com.amazon.tv.alexaalerts",
                    "com.amazon.tv.alexanotifications", "com.amazon.alexa.externalmediaplayer.fireos"),
            optional("Equipment control settings", "Setup screens for controlling a soundbar / AV receiver with the remote.",
                    "com.amazon.tv.devicecontrolsettings"),
            optional("Frustration-Free Setup", "Automatic setup of Amazon smart-home devices.",
                    "com.amazon.whisperjoin.middleware.np"),
            optional("Matter smart home", "Matter smart-home controller.",
                    "com.amazon.tv.matter", "com.amazon.tv.mattercohost"),
            optional("Fire TV Channels", "Amazon's free streaming channels (also uploads playback quality logs).",
                    "com.amazon.franktvinput", "com.amazon.hedwig"),
            optional("HbbTV / red button", "Interactive broadcast features (Vewd). Normal antenna channels work without it.",
                    "com.vewd.core.service", "com.vewd.core.tis", "com.vewd.core.tvapp",
                    "com.vewd.core.fvpplayer", "com.vewd.core.fvpsign"),
            optional("Built-in AirPlay", "Receive AirPlay from Apple devices.",
                    "com.apple.airplay.amazon.airplay"),
            optional("Forced update screen", "Forced-update screen and installer. The OTA service itself is protected, "
                            + "so this may not stop every update.",
                    "com.amazon.tv.forcedotaupdater.v2"),
            optional("Katoch command agent", "Appears to receive Amazon push commands; has permission to change secure "
                            + "settings and reboot. Exact purpose unclear.",
                    "com.amazon.katoch"),
            optional("Naatyam app lockdown", "Appears to be a remote-configured app lockdown / launch redirect.",
                    "com.amazon.naatyam"),
            optional("Device shadow", "Appears to mirror the device state to Amazon's cloud.",
                    "com.amazon.livedeviceservice"),
            optional("Prism profile sync", "Linked app profiles and content sync.",
                    "com.amazon.prism.android.service"),
            optional("Sale service", "Device ownership / sale state sync.",
                    "com.amazon.device.sale.service"),
            optional("Silk browser", "Amazon's web browser.",
                    "com.amazon.cloud9"),
            optional("Amazon Appstore", "Without it, apps can't be installed or updated from the Appstore.",
                    "com.amazon.venezia"),
            optional("Whisperplay", "Lets phones discover the TV (Fire TV remote app, some casting).",
                    "com.amazon.whisperlink.core.android"),
            optional("Automatic time zone", "Sets the clock's time zone automatically.",
                    "com.amazon.kindleautomatictimezone"),
            optional("Accessibility", "Screen reader, magnifier, text banner, hearing aids; also hosts the "
                            + "network-setup and language-change dialogs.",
                    "com.amazon.logan"),
            optional("Neo service host", "Hosts Amazon's Neo services, including DIAL casting from phones.",
                    "com.amazon.neodelegate"),
            optional("Pairing PIN", "PIN pairing for companion apps and devices.",
                    "com.amazon.tv.turnstile"),

            locked("Alexa voice", "Always-running system app; Fire OS restarts it immediately.",
                    "com.amazon.vizzini"),
            locked("Multi-room audio", "Always-running system app; keeps running even when disabled.",
                    "com.amazon.whad"),
            locked("Equipment control service", "Always-running system app; disabling it causes a crash loop.",
                    "com.amazon.tv.devicecontrol"),
            locked("Amazon sign-in", "Home screen, Appstore and registration depend on it.",
                    "com.amazon.imp"),
            locked("Amazon metrics", "Protected by Fire OS.",
                    "com.amazon.client.metrics"),
            locked("Metrics client library", "Shared code library loaded by the keyboard, Appstore, Alexa and others; "
                            + "disabling it would break them instead of stopping telemetry.",
                    "com.amazon.client.metrics.api"),
            locked("System updates", "Protected by Fire OS.",
                    "com.amazon.device.software.ota"),
            locked("Ad deep-links", "Protected by Fire OS.",
                    "com.amazon.ftvads.deeplinking"),
            locked("ADEP diagnostics", "Protected by Fire OS.",
                    "com.amazon.adep"),
            locked("Diode diagnostics", "Protected by Fire OS.",
                    "com.amazon.diode"),
            locked("Home screen", "The Fire TV launcher; without it the TV has no home screen.",
                    "com.amazon.tv.launcher"),
            locked("Settings", "Fire TV settings menus.",
                    "com.amazon.tv.settings.v2", "com.amazon.tv.settings.core"),
            locked("On-screen keyboard", "Needed for any text input.",
                    "com.amazon.tv.ime"),
            locked("Setup wizard", "Runs on first boot and after a reset.",
                    "com.amazon.tv.oobe"),
            locked("System WebView", "Many apps need it to show web content.",
                    "com.amazon.webview.chromium"),
            locked("Connectivity", NETWORK_CRITICAL,
                    "com.amazon.connectivitycontroller", "com.amazon.net.smartconnect", "com.amazon.tv.routing"));

    /**
     * Packages Fire OS refuses to disable ("Cannot disable a protected package"), found by trying each
     * uncatalogued package on one TV (Fire OS 7.7.1.2). Listed under "Can't be disabled".
     */
    static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
            "amazon.fireos", "com.amazon.device.crashmanager", "com.amazon.device.logmanager",
            "com.amazon.device.messaging", "com.amazon.device.messaging.sdk.internal.library",
            "com.amazon.device.messaging.sdk.library", "com.amazon.device.metrics", "com.amazon.device.rdmapplication",
            "com.amazon.device.settings.sdk.internal.library", "com.amazon.device.software.ota.override",
            "com.amazon.device.sync", "com.amazon.device.sync.sdk.internal", "com.amazon.firehomestarter",
            "com.amazon.identity.auth.device.authorization", "com.amazon.minerva.client.api", "com.amazon.platform.fdrw",
            "com.amazon.securitysyncclient", "com.amazon.sync.service", "com.amazon.tcomm.client", "com.amazon.tv.csapp",
            "com.amazon.tv.keypolicymanager", "com.amazon.tv.parentalcontrols", "com.fireos.arcus.proxy",
            "com.fireos.usagestats.proxy"));

    private static final String[] VENDOR_PREFIXES = {"com.amazon.", "amazon.", "com.fireos.", "com.ivona."};

    /** Amazon / Fire OS packages that aren't in the catalog are offered as "unknown"; overlays only carry resources. */
    static boolean isVendorPackage(String pkg) {
        if (pkg.contains(".overlay")) return false;
        for (String prefix : VENDOR_PREFIXES) {
            if (pkg.startsWith(prefix)) return true;
        }
        return false;
    }

    private Catalog() {
    }
}
