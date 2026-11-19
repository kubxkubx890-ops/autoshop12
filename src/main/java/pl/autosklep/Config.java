package pl.autosklep;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    /** Komenda otwierajaca sklep (bez ukosnika). */
    public String command = "sklep";
    /** Ile razy kliknac w miedz przed zamknieciem sklepu. */
    public int copperClicks = 1;
    /** Przerwa miedzy kliknieciami w tickach (0 = jak najszybciej). */
    public int clickDelay = 0;
    /** Max czas oczekiwania na menu w tickach (20 = 1 s). */
    public int timeoutTicks = 60;
    /** Przerwa po zamknieciu sklepu w tickach. */
    public int cooldownTicks = 20;
    /** Ile razy powtorzyc sprzedaz, jesli sie nie udala. */
    public int maxRetries = 5;
    /** Sprzedaz co X sekund (0 = wylaczone), niezaleznie od zapelnienia. */
    public int sellIntervalSeconds = 120;

    /** Wyrzucanie niepotrzebnych rzeczy (domyslnie WYLACZONE dla bezpieczenstwa). */
    public boolean dropEnabled = false;
    /** Wyrzucanie co X sekund. */
    public int dropIntervalSeconds = 60;
    /** Przerwa miedzy wyrzuceniami w tickach. */
    public int dropDelayTicks = 3;
    /** 36 slotow: 0-8 pasek, 9-35 ekwipunek. true = chroniony (nie wyrzucaj). */
    public boolean[] protectedSlots = new boolean[36];

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("autosklep.json");
    private static Config instance = new Config();

    public static Config get() {
        return instance;
    }

    private void fix() {
        if (protectedSlots == null || protectedSlots.length != 36) {
            boolean[] n = new boolean[36];
            if (protectedSlots != null) {
                System.arraycopy(protectedSlots, 0, n, 0, Math.min(36, protectedSlots.length));
            }
            protectedSlots = n;
        }
        if (command == null || command.isBlank()) command = "sklep";
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) instance = c;
            }
        } catch (Exception ignored) {}
        instance.fix();
        save();
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (Exception ignored) {}
    }
}
