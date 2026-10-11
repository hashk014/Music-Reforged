/* Music Reforged — made by hash014. GPL-2.0-or-later. */
package fr.hash014.music;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import org.bukkit.entity.Player;

/** Use the server's component API so it translates click events to the client's wire format. */
final class ChatLinks {
    private ChatLinks() { }
    static boolean send(Player player, String url) {
        try {
            Class<?> base = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> text = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Class<?> click = Class.forName("net.md_5.bungee.api.chat.ClickEvent");
            Class<?> action = Class.forName("net.md_5.bungee.api.chat.ClickEvent$Action");
            Class<?> color = Class.forName("net.md_5.bungee.api.ChatColor");
            Object message = text.getConstructor(String.class).newInstance("[Music Reforged] Audio administration (30-minute session): " + url);
            Object openUrl = action.getField("OPEN_URL").get(null);
            Object event = click.getConstructor(action, String.class).newInstance(openUrl, url);
            base.getMethod("setClickEvent", click).invoke(message, event);
            base.getMethod("setUnderlined", Boolean.class).invoke(message, Boolean.TRUE);
            base.getMethod("setColor", color).invoke(message, color.getField("AQUA").get(null));
            Object components = Array.newInstance(base, 1); Array.set(components, 0, message);
            Method spigot = Player.class.getMethod("spigot");
            spigot.getReturnType().getMethod("sendMessage", components.getClass()).invoke(spigot.invoke(player), new Object[] { components });
            return true;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }
}
