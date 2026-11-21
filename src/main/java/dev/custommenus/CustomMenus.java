package dev.custommenus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class CustomMenus extends JavaPlugin implements TabExecutor {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    private final Map<String, Menu> menus = new LinkedHashMap<>();
    private final Map<String, Command> bound = new HashMap<>();
    private File dataFile;
    private MenuListener gui;

    @Override
    public void onEnable() {
        dataFile = new File(getDataFolder(), "menus.yml");
        gui = new MenuListener(this);
        getServer().getPluginManager().registerEvents(gui, this);
        PluginCommand cmd = Objects.requireNonNull(getCommand("menu"));
        cmd.setExecutor(this);
        cmd.setTabCompleter(this);
        load();
        getLogger().info("=====================================");
        getLogger().info(" CustomMenus v" + getPluginMeta().getVersion());
        getLogger().info(" Stworzone przez Mapthio Code");
        getLogger().info("=====================================");
    }

    @Override
    public void onDisable() {
        save();
        for (String n : new ArrayList<>(bound.keySet())) unbind(n);
    }

    /** MiniMessage albo kolory &. */
    public static Component parse(String s) {
        if (s.indexOf('&') >= 0 && s.indexOf('<') < 0) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
        }
        return MM.deserialize(s);
    }

    // ------------------------------------------------------------ zapis / odczyt

    private void load() {
        for (String n : new ArrayList<>(bound.keySet())) unbind(n);
        menus.clear();
        if (!dataFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = y.getConfigurationSection("menus");
        if (root == null) return;
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) continue;
            Menu m = new Menu(name);
            m.title = s.getString("title", m.title);
            m.rows = Math.max(1, Math.min(6, s.getInt("rows", 3)));
            m.bind = s.getString("bind", "");
            ConfigurationSection ss = s.getConfigurationSection("slots");
            if (ss != null) {
                for (String k : ss.getKeys(false)) {
                    ConfigurationSection c = ss.getConfigurationSection(k);
                    if (c == null) continue;
                    Menu.Slot sl = new Menu.Slot();
                    sl.item = c.getItemStack("item");
                    sl.command = c.getString("command", "");
                    try {
                        sl.runAs = Menu.RunAs.valueOf(c.getString("run-as", "PLAYER"));
                    } catch (IllegalArgumentException ignored) { }
                    sl.close = c.getBoolean("close", true);
                    m.slots.put(Integer.parseInt(k), sl);
                }
            }
            menus.put(name, m);
            if (!m.bind.isEmpty()) {
                String err = bind(m, m.bind);
                if (err != null) getLogger().warning("Menu " + name + ": " + err);
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("CustomMenus - stworzone przez Mapthio Code"));
        for (Menu m : menus.values()) {
            m.slots.values().removeIf(s -> s.item == null && s.command.isEmpty());
            String p = "menus." + m.name;
            y.set(p + ".title", m.title);
            y.set(p + ".rows", m.rows);
            y.set(p + ".bind", m.bind);
            for (Map.Entry<Integer, Menu.Slot> e : m.slots.entrySet()) {
                String sp = p + ".slots." + e.getKey();
                Menu.Slot s = e.getValue();
                if (s.item != null) y.set(sp + ".item", s.item);
                y.set(sp + ".command", s.command);
                y.set(sp + ".run-as", s.runAs.name());
                y.set(sp + ".close", s.close);
            }
        }
        try {
            getDataFolder().mkdirs();
            y.save(dataFile);
        } catch (IOException ex) {
            getLogger().severe("Nie udalo sie zapisac menus.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ komenda do otwierania

    private String bind(Menu m, String cmdName) {
        cmdName = cmdName.toLowerCase();
        if (!cmdName.matches("[a-z0-9_-]+")) return "Nieprawidlowa nazwa komendy (a-z, 0-9, _ , -).";
        CommandMap map = getServer().getCommandMap();
        Command existing = map.getCommand(cmdName);
        if (existing != null && existing != bound.get(m.name)) return "Komenda /" + cmdName + " juz istnieje.";
        unbind(m.name);

        final String menuName = m.name;
        Command cmd = new Command(cmdName) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Tylko dla graczy.");
                    return true;
                }
                if (!p.hasPermission("custommenus.use")) {
                    p.sendMessage(parse("<red>Brak uprawnien."));
                    return true;
                }
                Menu mm = menus.get(menuName);
                if (mm != null) gui.open(p, mm, false);
                return true;
            }
        };
        cmd.setDescription("Menu " + m.name);
        map.register("custommenus", cmd);
        bound.put(m.name, cmd);
        m.bind = cmdName;
        getServer().getOnlinePlayers().forEach(Player::updateCommands);
        return null;
    }

    private void unbind(String menuName) {
        Command cmd = bound.remove(menuName);
        if (cmd == null) return;
        CommandMap map = getServer().getCommandMap();
        cmd.unregister(map);
        map.getKnownCommands().values().removeIf(c -> c == cmd);
        getServer().getOnlinePlayers().forEach(Player::updateCommands);
    }

    // ------------------------------------------------------------ /menu

    private void msg(CommandSender s, String text) {
        s.sendMessage(parse(text));
    }

    private void help(CommandSender s) {
        msg(s, "<gold><bold>CustomMenus</bold> <gray>by <gradient:#ff6a00:#ee0979>Mapthio Code</gradient> <gray>- komendy:");
        msg(s, "<yellow>/menu create <nazwa> [rzedy] <gray>- nowe menu");
        msg(s, "<yellow>/menu edit <nazwa> <gray>- edytor (kliknij slot)");
        msg(s, "<yellow>/menu open <nazwa> [gracz] <gray>- otworz menu");
        msg(s, "<yellow>/menu bind <nazwa> <komenda> <gray>- wlasna komenda otwierajaca menu");
        msg(s, "<yellow>/menu unbind <nazwa> <gray>- usun komende");
        msg(s, "<yellow>/menu title <nazwa> <tekst> <gray>- tytul (MiniMessage / &)");
        msg(s, "<yellow>/menu rows <nazwa> <1-6> <gray>- liczba rzedow");
        msg(s, "<yellow>/menu fill <nazwa> <material> <gray>- wypelnij puste sloty");
        msg(s, "<yellow>/menu delete <nazwa>, /menu list, /menu reload, /menu about");
        msg(s, "<dark_gray>Stworzone przez <gradient:#ff6a00:#ee0979>Mapthio Code</gradient>");
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            help(s);
            return true;
        }
        String sub = a[0].toLowerCase();
        boolean admin = s.hasPermission("custommenus.admin");

        if (sub.equals("about")) {
            msg(s, "<gold><bold>CustomMenus</bold> <gray>v" + getPluginMeta().getVersion());
            msg(s, "<gray>Stworzone przez <gradient:#ff6a00:#ee0979><bold>Mapthio Code</bold></gradient>");
            return true;
        }

        if (sub.equals("open")) {
            if (a.length < 2) { msg(s, "<red>/menu open <nazwa> [gracz]"); return true; }
            Menu m = menus.get(a[1].toLowerCase());
            if (m == null) { msg(s, "<red>Nie ma takiego menu."); return true; }
            Player target;
            if (a.length >= 3) {
                if (!admin) { msg(s, "<red>Brak uprawnien."); return true; }
                target = getServer().getPlayerExact(a[2]);
                if (target == null) { msg(s, "<red>Gracz offline."); return true; }
            } else if (s instanceof Player p) {
                if (!p.hasPermission("custommenus.use")) { msg(s, "<red>Brak uprawnien."); return true; }
                target = p;
            } else { msg(s, "<red>Podaj gracza."); return true; }
            gui.open(target, m, false);
            return true;
        }

        if (!admin) { msg(s, "<red>Brak uprawnien."); return true; }

        switch (sub) {
            case "list" -> {
                if (menus.isEmpty()) { msg(s, "<gray>Brak menu."); return true; }
                for (Menu m : menus.values()) {
                    msg(s, "<yellow>" + m.name + " <gray>(" + m.rows + " rzedy"
                            + (m.bind.isEmpty() ? "" : ", /" + m.bind) + ")");
                }
            }
            case "reload" -> {
                load();
                msg(s, "<green>Przeladowano menus.yml.");
            }
            case "create" -> {
                if (a.length < 2) { msg(s, "<red>/menu create <nazwa> [rzedy]"); return true; }
                String name = a[1].toLowerCase();
                if (!name.matches("[a-z0-9_-]+")) { msg(s, "<red>Nazwa: tylko a-z, 0-9, _ , -"); return true; }
                if (menus.containsKey(name)) { msg(s, "<red>Takie menu juz istnieje."); return true; }
                Menu m = new Menu(name);
                if (a.length >= 3) {
                    try { m.rows = Math.max(1, Math.min(6, Integer.parseInt(a[2]))); }
                    catch (NumberFormatException ignored) { }
                }
                menus.put(name, m);
                save();
                msg(s, "<green>Utworzono menu <white>" + name + "<green>. Uzyj <yellow>/menu edit " + name);
            }
            case "edit" -> {
                if (!(s instanceof Player p)) { msg(s, "<red>Tylko gracz."); return true; }
                if (a.length < 2) { msg(s, "<red>/menu edit <nazwa>"); return true; }
                Menu m = menus.get(a[1].toLowerCase());
                if (m == null) { msg(s, "<red>Nie ma takiego menu."); return true; }
                gui.open(p, m, true);
                msg(s, "<gray>Wez item w kursor i kliknij slot, albo kliknij pusty kursor na slocie by edytowac.");
            }
            case "delete" -> {
                if (a.length < 2) { msg(s, "<red>/menu delete <nazwa>"); return true; }
                String name = a[1].toLowerCase();
                if (menus.remove(name) == null) { msg(s, "<red>Nie ma takiego menu."); return true; }
                unbind(name);
                save();
                msg(s, "<green>Usunieto menu.");
            }
            case "title" -> {
                if (a.length < 3) { msg(s, "<red>/menu title <nazwa> <tekst>"); return true; }
                Menu m = menus.get(a[1].toLowerCase());
                if (m == null) { msg(s, "<red>Nie ma takiego menu."); return true; }
                m.title = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
                save();
                msg(s, "<green>Ustawiono tytul: <reset>" + m.title);
            }
            case "rows" -> {
                if (a.length < 3) { msg(s, "<red>/menu rows <nazwa> <1-6>"); return true; }
                Menu m = menus.get(a[1].toLowerCase());
                if (m == null) { msg(s, "<red>Nie ma takiego menu."); return true; }
                try {
                    m.rows = Math.max(1, Math.min(6, Integer.parseInt(a[2])));
                } catch (NumberFormatException e) { msg(s, "<red>Podaj liczbe 1-6."); return true; }
                m.slots.keySet().removeIf(i -> i >= m.rows * 9);
                save();
                msg(s, "<green>Ustawiono rzedy: " + m.rows);
            }
            case "bind" -> {
                if (a.length < 3) { msg(s, "<red>/menu bind <nazwa> <komenda>"); return true; }
                Menu m = menus.get(a[1].toLowerCase());
                if (m == null) { msg(s, "<red>Nie ma takiego menu."); return true; }
                String err = bind(m, a[2].startsWith("/") ? a[2].substring(1) : a[2]);
                if (err != null) { msg(s, "<red>" + err); return true; }
                save();
                msg(s, "<green>Menu otwierane teraz komenda <yellow>/" + m.bind);
            }
            case "unbind" -> {
                if (a.length < 2) { msg(s, "<red>/menu unbind <nazwa>"); return true; }
                Menu m = menus.get(a[1].toLowerCase());
                if (m == null) { msg(s, "<red>Nie ma takiego menu."); return true; }
                unbind(m.name);
                m.bind = "";
                save();
                msg(s, "<green>Usunieto komende.");
            }
            case "fill" -> {
                if (a.length < 3) { msg(s, "<red>/menu fill <nazwa> <material>"); return true; }
                Menu m = menus.get(a[1].toLowerCase());
                if (m == null) { msg(s, "<red>Nie ma takiego menu."); return true; }
                Material mat = Material.matchMaterial(a[2]);
                if (mat == null || !mat.isItem()) { msg(s, "<red>Zly material."); return true; }
                for (int i = 0; i < m.rows * 9; i++) {
                    Menu.Slot sl = m.slots.get(i);
                    if (sl != null && sl.item != null) continue;
                    if (sl == null) { sl = new Menu.Slot(); m.slots.put(i, sl); }
                    sl.item = MenuListener.button(mat, " ");
                }
                save();
                msg(s, "<green>Wypelniono puste sloty.");
            }
            default -> help(s);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : List.of("help", "open", "create", "edit", "delete", "list", "title", "rows",
                    "bind", "unbind", "fill", "reload", "about")) {
                if (x.startsWith(a[0].toLowerCase())) out.add(x);
            }
        } else if (a.length == 2 && !a[0].equalsIgnoreCase("create") && !a[0].equalsIgnoreCase("list")) {
            for (String n : menus.keySet()) if (n.startsWith(a[1].toLowerCase())) out.add(n);
        } else if (a.length == 3 && a[0].equalsIgnoreCase("fill")) {
            for (Material m : List.of(Material.GRAY_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE,
                    Material.WHITE_STAINED_GLASS_PANE)) {
                if (m.name().toLowerCase().startsWith(a[2].toLowerCase())) out.add(m.name().toLowerCase());
            }
        } else if (a.length == 3 && a[0].equalsIgnoreCase("rows")) {
            out.addAll(List.of("1", "2", "3", "4", "5", "6"));
        }
        return out;
    }
}
