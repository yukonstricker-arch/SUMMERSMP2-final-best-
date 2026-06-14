package net.summersmp.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Greets players when they join. */
public class JoinListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(Component.text("\u2726 Welcome to Summer SMP 2! \u2726", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("This is a Lifesteal server \u2014 kill players to steal their hearts. "
                + "Hit 0 and you're out until someone revives you.", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("\u2022 Only 3 Maces can exist on the whole server", NamedTextColor.GRAY));
        player.sendMessage(Component.text("\u2022 End crystals are disabled", NamedTextColor.GRAY));
        player.sendMessage(Component.text("\u2022 Commands: /rtp  /spawn  /tpa <player>  /tpaccept  /tpdeny", NamedTextColor.AQUA));
        player.sendMessage(Component.text("Good luck \u2014 don't lose all your hearts!", NamedTextColor.RED));
    }
}
