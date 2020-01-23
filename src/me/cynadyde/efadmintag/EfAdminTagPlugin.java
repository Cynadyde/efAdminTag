package me.cynadyde.efadmintag;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.MetaNode;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The main class of the plugin.
 */
@SuppressWarnings("WeakerAccess")
public class EfAdminTagPlugin extends JavaPlugin {

    private static final String CHAT_TAG;
    private static final List<String> STAFF_GROUPS;
    private static final List<String> OP_GROUPS;
    private static final String HIDDEN_GROUP_KEY;

    private PluginCommand adminTagCmd;
    private LuckPerms luckPermsApi;

    static {
        CHAT_TAG = ChatColor.translateAlternateColorCodes('&', "&0[&bEF&0]&r ");
        STAFF_GROUPS = Collections.unmodifiableList(new ArrayList<>(Arrays.asList("owner", "coowner", "rootadmin", "admin", "moderator", "tmod")));
        OP_GROUPS = Collections.unmodifiableList(new ArrayList<>(Arrays.asList("owner", "coowner", "rootadmin", "admin")));
        HIDDEN_GROUP_KEY = "ef.group.disabled";
    }

    @Override
    public void onEnable() {

        adminTagCmd = Objects.requireNonNull(getCommand("admintag"));
        adminTagCmd.setExecutor(this);

        luckPermsApi = LuckPermsProvider.get();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (command != adminTagCmd) {
            return false;
        }
        if (!(sender instanceof Player)) {
            sendMessage(sender, "&cYou must be a player to use that command!");
            return false;
        }
        return toggleAdminTag((Player) sender);
    }

    /**
     * Attempts to toggle the admin tag of the given player, looking at
     * only the left-most staff group they are a member of, if at all.
     *
     * @return true if the operation succeeded
     */
    public boolean toggleAdminTag(Player player) {

        User user = luckPermsApi.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            sendMessage(player, "&cYou are not in the permissions system...");
            return false;
        }
        String group = getUserStaffMembership(user);
        String hiddenGroup = getUserHiddenGroupTag(user);

        /*
         * If the user has admin-tag disabled and then their staff rank changes,
         * they would have a group AND an inaccurate hiddenGroup. Checking group
         * first, with getUserStaffMembership() returning the left-most group
         * (ordered highest to lowest), avoids any issues caused by this.
         */

        // DISABLE ADMIN TAG...
        if (group != null) {
            if (setUserMembership(user, group, false).wasSuccessful()
                    && setUserHiddenGroupTag(user, group).wasSuccessful()) {

                if (OP_GROUPS.contains(group)) {
                    player.setOp(false);
                }
                sendMessage(player, "&aRemoving Admin Tag");
                luckPermsApi.getUserManager().saveUser(user);

                getLogger().info(String.format(
                        "toggling off %s %s's tag",
                        group, player.getName()));
                return true;
            }
            else {
                getLogger().warning(String.format(
                        "unable to toggle off %s %s's tag",
                        group, player.getName()));
                return false;
            }
        }
        // ENABLE ADMIN TAG...
        else if (hiddenGroup != null) {
            if (setUserMembership(user, hiddenGroup, true).wasSuccessful()
                    && setUserHiddenGroupTag(user, null).wasSuccessful()) {

                if (OP_GROUPS.contains(hiddenGroup)) {
                    player.setOp(true);
                }
                sendMessage(player, "&aAdding Admin Tag");
                luckPermsApi.getUserManager().saveUser(user);

                getLogger().info(String.format(
                        "toggling on %s %s's tag",
                        hiddenGroup, player.getName()));
                return true;
            }
            else {
                getLogger().warning(String.format(
                        "unable to toggle on %s %s's tag",
                        hiddenGroup, player.getName()));
                return false;
            }
        }
        // NO PERMISSION...
        else {
            sendMessage(player, "&cYou do not have permission to do that!");
            return false;
        }
    }

    /**
     * Formats the given message with '&' to {@link ChatColor} and {@link String#format},
     * prefixes it with the plugin's chat tag, and sends it to the given command sender.
     */
    private static void sendMessage(@NotNull CommandSender sender, @NotNull String message, @Nullable Object... objects) {

        sender.sendMessage(CHAT_TAG + String.format(ChatColor.translateAlternateColorCodes('&', message), objects));
    }

    /**
     * Gets the leftmost name in the staff groups list that the user is a member of, or null.
     */
    private @Nullable String getUserStaffMembership(@NotNull User user) {

        List<String> userGroups = user.getNodes().stream()
                .filter(NodeType.INHERITANCE::matches)
                .map(NodeType.INHERITANCE::cast)
                .map(InheritanceNode::getGroupName)
                .collect(Collectors.toList());

        for (String group : STAFF_GROUPS) {
            if (userGroups.contains(group)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Gets the value of the hidden group meta element if the player
     */
    private @Nullable String getUserHiddenGroupTag(@NotNull User user) {

        for (MetaNode node : user.getNodes().stream()
                .filter(NodeType.META::matches)
                .map(NodeType.META::cast)
                .collect(Collectors.toList())) {

            if (node.getMetaKey().equals(HIDDEN_GROUP_KEY)) {
                return node.getMetaValue();
            }
        }
        return null;
    }

    /**
     * Sets the given user's membership in the given group to the given state.
     */
    private DataMutateResult setUserMembership(@NotNull User user, @NotNull String group, boolean isMember) {

        InheritanceNode node = InheritanceNode.builder().group(group).build();
        NodeMap data = user.data();

        for (Node n : data.toCollection()) {
            if (n.getKey().equals(node.getKey())) {
                data.remove(n);
            }
        }
        if (isMember) {
            return data.add(node);
        }
        else {
            return DataMutateResult.SUCCESS;
        }
    }

    /**
     * Sets the value of the user's hidden group meta element, or removes it if null.
     */
    private DataMutateResult setUserHiddenGroupTag(@NotNull User user, @Nullable String value) {

        NodeMap data = user.data();

        for (Node n : data.toCollection()) {
            if (NodeType.META.matches(n)) {
                if (NodeType.META.cast(n).getMetaKey().equals(HIDDEN_GROUP_KEY)) {
                    data.remove(n);
                }
            }
        }
        if (value != null) {
            return data.add(MetaNode.builder().key(HIDDEN_GROUP_KEY).value(value).build());
        }
        else {
            return DataMutateResult.SUCCESS;
        }
    }
}
