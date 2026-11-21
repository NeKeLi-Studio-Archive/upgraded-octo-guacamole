package dev.custommenus;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.TreeMap;

public class Menu {

    public enum RunAs { PLAYER, CONSOLE }

    public static class Slot {
        public ItemStack item;
        public String command = "";
        public RunAs runAs = RunAs.PLAYER;
        public boolean close = true;
    }

    public final String name;
    public String title;
    public int rows = 3;
    public String bind = "";
    public final Map<Integer, Slot> slots = new TreeMap<>();

    public Menu(String name) {
        this.name = name;
        this.title = "<white>" + name;
    }
}
