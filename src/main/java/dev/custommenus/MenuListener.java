package dev.custommenus;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class MenuListener implements Listener {

    static class MenuHolder implements InventoryHolder {
        final Menu menu;
        final boolean edit;
        Inventory inv;
        MenuHolder(Menu menu, boolean edit) { this.menu = menu; this.edit = edit; }
        @Override public Inventory getInventory() { return inv; }
    }

    static class EditorHolder implements InventoryHolder {
        final Menu menu;
        final int slot;
        Inventory inv;
        EditorHolder(Menu menu, int slot) { this.menu = menu; this.slot = slot; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final CustomMenus plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public MenuListener(CustomMenus plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ helpery

    static Component line(String s) {
        return CustomMenus.parse(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    static String esc(String s) {
        return CustomMenus.MM.escapeTags(s);
    }

    static ItemStack button(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        it.editMeta(m -> {
            m.displayName(line(name));
            if (lore.length > 0) {
                List<Component> l = new ArrayList<>();
                for (String x : lore) l.add(line(x));
                m.lore(l);
            }
        });
        return it;
    }

    // ------------------------------------------------------------ otwieranie

    public void open(Player p, Menu m, boolean edit) {
        MenuHolder h = new MenuHolder(m, edit);
        Component title = edit
                ? CustomMenus.parse("<dark_gray>Edycja: <white>" + m.name)
                : CustomMenus.parse(m.title);
        Inventory inv = Bukkit.createInventory(h, m.rows * 9, title);
        h.inv = inv;
        render(inv, m, edit);
        p.openInventory(inv);
    }

    private void render(Inventory inv, Menu m, boolean edit) {
        inv.clear();
        for (Map.Entry<Integer, Menu.Slot> e : m.slots.entrySet()) {
            int i = e.getKey();
            Menu.Slot s = e.getValue();
            if (i >= inv.getSize()) continue;
            ItemStack it;
            if (s.item == null) {
                if (!edit) continue;
                it = button(Material.GRAY_STAINED_GLASS_PANE, "<gray>(brak itemu)");
            } else {
                it = s.item.clone();
            }
            if (edit) {
                it.editMeta(meta -> {
                    List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                    lore.add(Component.empty());
                    lore.add(line("<dark_gray>Slot " + i));
                    lore.add(line("<gray>Komenda: <yellow>" + (s.command.isEmpty() ? "brak" : esc(s.command))));
                    meta.lore(lore);
                });
            }
            inv.setItem(i, it);
        }
    }

    private void openEditor(Player p, Menu m, int slot) {
        Menu.Slot s = m.slots.computeIfAbsent(slot, k -> new Menu.Slot());
        EditorHolder h = new EditorHolder(m, slot);
        Inventory inv = Bukkit.createInventory(h, 27, CustomMenus.parse("<dark_gray>Slot " + slot + " - " + m.name));
        h.inv = inv;
        fillEditor(inv, s);
        p.openInventory(inv);
    }

    private void fillEditor(Inventory inv, Menu.Slot s) {
        inv.clear();
        ItemStack bg = button(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);
        inv.setItem(4, s.item == null ? button(Material.BARRIER, "<red>Brak itemu") : s.item.clone());
        inv.setItem(10, button(Material.CHEST, "<green>Ustaw item",
                "<gray>Kliknij trzymajac item na kursorze", "<gray>albo majac go w rece"));
        inv.setItem(11, button(Material.NAME_TAG, "<yellow>Ustaw nazwe",
                "<gray>MiniMessage lub kolory &"));
        inv.setItem(12, button(Material.WRITABLE_BOOK, "<yellow>Ustaw opis (lore)",
                "<gray>Linie oddzielaj znakiem |", "<gray>'clear' usuwa opis"));
        inv.setItem(13, button(Material.COMMAND_BLOCK, "<gold>Ustaw komende",
                "<gray>Obecna: <white>" + (s.command.isEmpty() ? "brak" : esc(s.command)),
                "<gray>Kilka komend: oddziel ;;",
                "<gray>%player% = nick gracza"));
        inv.setItem(14, button(Material.REDSTONE_TORCH, "<aqua>Wykonaj jako: <white>" + s.runAs.name(),
                "<gray>Kliknij aby zmienic (PLAYER / CONSOLE)"));
        inv.setItem(15, button(Material.OAK_DOOR, "<aqua>Zamknij po kliknieciu: <white>" + (s.close ? "TAK" : "NIE"),
                "<gray>Kliknij aby zmienic"));
        inv.setItem(16, button(Material.TNT, "<red>Usun slot"));
        inv.setItem(22, button(Material.ARROW, "<gray>Wroc do menu"));
        inv.setItem(26, button(Material.NETHER_STAR, "<gradient:#ff6a00:#ee0979>Mapthio Code</gradient>",
                "<gray>Plugin stworzony przez Mapthio Code"));
    }

    // ------------------------------------------------------------ klikanie

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (!(holder instanceof MenuHolder) && !(holder instanceof EditorHolder)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        boolean editing = holder instanceof EditorHolder || (holder instanceof MenuHolder mh0 && mh0.edit);
        boolean top = e.getClickedInventory() == e.getView().getTopInventory();

        if (!top) {
            if (!editing) {
                e.setCancelled(true);
            } else if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
            }
            return;
        }

        e.setCancelled(true);
        int slot = e.getSlot();
        ItemStack cursor = e.getCursor();
        boolean hasCursor = cursor != null && !cursor.getType().isAir();

        if (holder instanceof MenuHolder mh) {
            if (!mh.edit) {
                Menu.Slot s = mh.menu.slots.get(slot);
                if (s != null && !s.command.isEmpty()) run(p, s);
                return;
            }
            if (hasCursor) {
                Menu.Slot s = mh.menu.slots.computeIfAbsent(slot, k -> new Menu.Slot());
                s.item = cursor.clone();
                s.item.setAmount(1);
                plugin.save();
                render(mh.inv, mh.menu, true);
            } else if (e.getClick() != ClickType.DOUBLE_CLICK) {
                Bukkit.getScheduler().runTask(plugin, () -> openEditor(p, mh.menu, slot));
            }
            return;
        }

        EditorHolder eh = (EditorHolder) holder;
        Menu.Slot s = eh.menu.slots.computeIfAbsent(eh.slot, k -> new Menu.Slot());

        switch (slot) {
            case 10 -> {
                ItemStack src = hasCursor ? cursor : p.getInventory().getItemInMainHand();
                if (src.getType().isAir()) {
                    p.sendMessage(CustomMenus.parse("<red>Wez item w kursor lub do reki."));
                    return;
                }
                s.item = src.clone();
                s.item.setAmount(1);
                plugin.save();
                fillEditor(eh.inv, s);
            }
            case 11 -> ask(p, eh, "<yellow>Wpisz na czacie nowa nazwe itemu.", t -> {
                if (s.item == null) {
                    p.sendMessage(CustomMenus.parse("<red>Najpierw ustaw item."));
                    return;
                }
                s.item.editMeta(m -> m.displayName(line(t)));
            });
            case 12 -> ask(p, eh, "<yellow>Wpisz opis. Linie oddziel znakiem |", t -> {
                if (s.item == null) {
                    p.sendMessage(CustomMenus.parse("<red>Najpierw ustaw item."));
                    return;
                }
                s.item.editMeta(m -> {
                    if (t.equalsIgnoreCase("clear")) {
                        m.lore(null);
                        return;
                    }
                    List<Component> l = new ArrayList<>();
                    for (String x : t.split("\\|")) l.add(line(x.trim()));
                    m.lore(l);
                });
            });
            case 13 -> ask(p, eh, "<yellow>Wpisz komende (bez /). Kilka: oddziel ;; ; <white>%player%<yellow> = nick. 'clear' usuwa.", t -> {
                if (t.equalsIgnoreCase("clear")) {
                    s.command = "";
                    return;
                }
                s.command = t.startsWith("/") ? t.substring(1) : t;
            });
            case 14 -> {
                s.runAs = s.runAs == Menu.RunAs.PLAYER ? Menu.RunAs.CONSOLE : Menu.RunAs.PLAYER;
                plugin.save();
                fillEditor(eh.inv, s);
            }
            case 15 -> {
                s.close = !s.close;
                plugin.save();
                fillEditor(eh.inv, s);
            }
            case 16 -> {
                eh.menu.slots.remove(eh.slot);
                plugin.save();
                Bukkit.getScheduler().runTask(plugin, () -> open(p, eh.menu, true));
            }
            case 22 -> {
                plugin.save();
                Bukkit.getScheduler().runTask(plugin, () -> open(p, eh.menu, true));
            }
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (!(holder instanceof MenuHolder) && !(holder instanceof EditorHolder)) return;
        int size = e.getView().getTopInventory().getSize();
        if (e.getRawSlots().stream().anyMatch(i -> i < size)) e.setCancelled(true);
    }

    // ------------------------------------------------------------ czat jako input

    private void ask(Player p, EditorHolder eh, String prompt, Consumer<String> apply) {
        Bukkit.getScheduler().runTask(plugin, p::closeInventory);
        pending.put(p.getUniqueId(), text -> {
            if (!text.equalsIgnoreCase("cancel")) apply.accept(text);
            plugin.save();
            openEditor(p, eh.menu, eh.slot);
        });
        p.sendMessage(CustomMenus.parse(prompt + " <gray>(wpisz <red>cancel<gray> aby anulowac)"));
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Consumer<String> cb = pending.remove(e.getPlayer().getUniqueId());
        if (cb == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(plugin, () -> cb.accept(text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------ wykonywanie komend

    private void run(Player p, Menu.Slot s) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (s.close) p.closeInventory();
            for (String raw : s.command.split(";;")) {
                String c = raw.trim();
                if (c.isEmpty()) continue;
                if (c.startsWith("/")) c = c.substring(1);
                c = c.replace("%player%", p.getName());
                if (s.runAs == Menu.RunAs.CONSOLE) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
                } else {
                    p.performCommand(c);
                }
            }
        });
    }
}
