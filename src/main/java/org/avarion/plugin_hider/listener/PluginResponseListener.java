package org.avarion.plugin_hider.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.AdventureComponentConverter;
import org.avarion.plugin_hider.PluginHider;
import org.avarion.plugin_hider.util.Constants;
import org.avarion.plugin_hider.util.ReceivedPackets;
import org.avarion.plugin_hider.util.Reflection;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

public class PluginResponseListener extends PacketAdapter {
    final private PluginHider plugin;

    public PluginResponseListener(PluginHider plugin) {
        super(plugin, ListenerPriority.HIGHEST, Arrays.asList(PacketType.Play.Server.SYSTEM_CHAT, PacketType.Play.Client.CHAT_COMMAND));

        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(@NotNull PacketEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CHAT_COMMAND) {
            return;
        }

        String cmd = event.getPacket().getModifier().read(0).toString().toLowerCase();
        if (!Constants.isPluginCmd("/" + cmd.split(" ", 2)[0])) {
            return;
        }

        plugin.cachedUsers.put(event.getPlayer().getUniqueId(), new ReceivedPackets(plugin.getMyConfig(), 10));
    }

    @Override
    public void onPacketSending(@NotNull PacketEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.SYSTEM_CHAT) {
            return;
        }

        UUID player = event.getPlayer().getUniqueId();
        if (!plugin.cachedUsers.containsKey(player)) {
            return;
        }

        ReceivedPackets entry = plugin.cachedUsers.get(player);

        String text = readChatMessage(event.getPacket());
        entry.addSystemChatLine(text);

        if (entry.amountOfPlugins == 0) {
            // No plugins...
            plugin.cachedUsers.remove(player);
            return;
        }

        // Don't send out the original text.
        event.setCancelled(true);

        if (entry.isFinished()) {
            // Remove this from our cache, so we don't intercept it again
            plugin.cachedUsers.remove(player);
            // Send messages if there is anything to send.
            entry.sendModifiedMessage(event.getPlayer());
        }
    }

    private @Nullable String readChatMessage(PacketContainer packet) {
        String json = null;
        try {
            json = packet.getChatComponents().read(0).getJson();
        }
        catch (Exception ignored) {
            json = packet.getStrings().read(0);
        }

        if (json != null) {
            return json;
        }

        try {
            // https://github.com/dmulloy2/ProtocolLib/issues/2330
            final StructureModifier<Object> adventureModifier = packet.getModifier()
                                                                      .withType(AdventureComponentConverter.getComponentClass());

            if (!adventureModifier.getFields().isEmpty()) {
                final Object comp = adventureModifier.read(0);

                final Class<?> clazz = Reflection.getClass("net.kyori.adventure.text.serializer.gson.GsonComponentSerializer");
                final Object gson = Reflection.callStatic(clazz, "gson");

                final Class<?> component = Reflection.getClass("net.kyori.adventure.text.Component");
                final Method gsonMethod = Reflection.getMethod(gson.getClass(), "serialize", component);

                return (String) gsonMethod.invoke(gson, comp);
            }
        }
        catch (Throwable ignored) {
        }
        //
        //        final Object adventureContent = Reflection.getFieldContent(event.getPacket().getHandle(), "adventure$content");
        //
        //        if (adventureContent != null) {
        //            final List<String> contents = new ArrayList<>();
        //
        //            this.mergeChildren(adventureContent, contents);
        //            final String mergedContents = String.join("", contents);
        //
        //            return mergedContents;
        //        }
        return null;
    }
}