package me.saiintbrisson.bukkit.command.command;

import lombok.Getter;
import me.saiintbrisson.bukkit.command.BukkitFrame;
import me.saiintbrisson.bukkit.command.executor.BukkitCommandExecutor;
import me.saiintbrisson.bukkit.command.target.BukkitTargetValidator;
import me.saiintbrisson.minecraft.command.command.CommandHolder;
import me.saiintbrisson.minecraft.command.command.CommandInfo;
import me.saiintbrisson.minecraft.command.executor.CommandExecutor;
import me.saiintbrisson.minecraft.command.executor.CompleterExecutor;
import me.saiintbrisson.minecraft.command.message.MessageHolder;
import me.saiintbrisson.minecraft.command.message.MessageType;
import org.apache.commons.lang.StringUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author SaiintBrisson
 */

@Getter
public class BukkitCommand extends Command implements CommandHolder<CommandSender, BukkitChildCommand> {

    private final BukkitFrame frame;
    private final MessageHolder messageHolder;

    private CommandInfo commandInfo;

    private final int position;

    private CommandExecutor<CommandSender> commandExecutor;
    private CompleterExecutor<CommandSender> completerExecutor;

    private final List<BukkitChildCommand> childCommandList;

    public BukkitCommand(BukkitFrame frame, String name, int position) {
        super(name);

        this.frame = frame;
        this.position = position;

        this.messageHolder = frame.getMessageHolder();

        this.childCommandList = new ArrayList<>();
    }

    public final void initCommand(CommandInfo commandInfo, CommandExecutor<CommandSender> commandExecutor) {
        if (this.commandInfo != null) {
            throw new IllegalStateException("Command already initialized");
        }

        this.commandInfo = commandInfo;
        this.commandExecutor = commandExecutor;

        setAliases(Arrays.asList(commandInfo.getAliases()));

        if (commandInfo.getPermission() != null) {
            setPermission(commandInfo.getPermission());
        }

        final String usage = commandInfo.getUsage();
        if (usage != null && !usage.isEmpty()) {
            setUsage(usage);
        } else if (commandExecutor instanceof BukkitCommandExecutor) {
            setUsage(((BukkitCommandExecutor) commandExecutor).getParser().buildUsage(getFancyName()));
        }

        if (commandInfo.getDescription() != null) {
            setDescription(commandInfo.getDescription());
        }

        if (commandExecutor instanceof BukkitCommandExecutor) {
            ((BukkitCommandExecutor) commandExecutor).setCommand(this);
        }
    }

    public final void initCompleter(CompleterExecutor<CommandSender> completerExecutor) {
        if (this.completerExecutor != null) {
            throw new IllegalStateException("Completer already initialized");
        }

        this.completerExecutor = completerExecutor;
    }

    @Override
    public CommandExecutor<CommandSender> getCommandExecutor() {
        return commandExecutor;
    }

    @Override
    public BukkitChildCommand getChildCommand(String name) {
        for (BukkitChildCommand childCommand : childCommandList) {
            if (childCommand.equals(name)) return childCommand;
        }

        return null;
    }

    @Override
    public String getFancyName() {
        return getName();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!frame.getPlugin().isEnabled()) {
            return false;
        }

        if (!testPermissionSilent(sender)) {
            sender.sendMessage(
              messageHolder.getReplacing(MessageType.NO_PERMISSION, getPermission())
            );
            return false;
        }

        if (!BukkitTargetValidator.INSTANCE.validate(commandInfo.getTarget(), sender)) {
            sender.sendMessage(frame.getMessageHolder().getReplacing(
              MessageType.INCORRECT_TARGET,
              commandInfo.getTarget().name()
            ));
            return false;
        }

        if (args.length > 0) {
            BukkitChildCommand command = getChildCommand(args[0]);
            if (command != null) {
                final String label = commandLabel + " " + args[0];
                return command.execute(sender, label, Arrays.copyOfRange(args, 1, args.length));
            }
        }

        if (commandExecutor == null) {
            return false;
        }

        return commandExecutor.execute(new BukkitContext(
          commandLabel,
          sender,
          BukkitTargetValidator.INSTANCE.fromSender(sender),
          args
        ));
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias,
                                             @NotNull String[] args) throws IllegalArgumentException {
        if (completerExecutor != null) {
            return completerExecutor.execute(new BukkitContext(
              alias,
              sender,
              BukkitTargetValidator.INSTANCE.fromSender(sender),
              args
            ));
        }

        if (childCommandList.size() != 0 && args.length != 0) {
            List<String> matchedChildCommands = new ArrayList<>();

            for (BukkitChildCommand command : childCommandList) {
                if (StringUtils.startsWithIgnoreCase(command.getName(), args[args.length - 1])) {
                    matchedChildCommands.add(command.getName());
                }
            }

            if (matchedChildCommands.size() != 0) {
                Collections.sort(matchedChildCommands, String.CASE_INSENSITIVE_ORDER);
                return matchedChildCommands;
            }
        }

        return super.tabComplete(sender, alias, args);
    }

    public BukkitCommand createRecursive(String name) {
        int position = getPosition() + StringUtils.countMatches(name, ".");
        if (position == getPosition()) {
            return this;
        }

        String subName = name.substring(Math.max(name.indexOf('.') + 1, 0));

        int index = subName.indexOf('.');
        String nextSubCommand = subName;
        if (index != -1) {
            nextSubCommand = subName.substring(0, index);
        }

        BukkitChildCommand childCommand = getChildCommand(nextSubCommand);

        if (childCommand == null) {
            childCommand = new BukkitChildCommand(frame, nextSubCommand, this);
            getChildCommandList().add(childCommand);
        }

        return childCommand.createRecursive(subName);
    }

    public boolean equals(String name) {
        if (getName().equalsIgnoreCase(name)) {
            return true;
        }

        for (String alias : getAliases()) {
            if (alias.equalsIgnoreCase(name)) return true;
        }

        return false;
    }

}
