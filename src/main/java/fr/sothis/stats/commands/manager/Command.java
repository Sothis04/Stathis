package fr.sothis.stats.commands.manager;

import fr.sothis.stats.commands.manager.utils.Perms;
import fr.sothis.stats.commands.manager.arg.ArgReader;
import fr.sothis.stats.config.Config;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Command {

    private static final Pattern OPTION_PATTERN = Pattern.compile("([<\\[])([a-zA-Z0-9]+)(?:[=]'([^'\\\\]*(?:\\\\.[^'\\\\]*)*)')?(?:[:]([a-zA-Z0-9]+))?[>\\]]");

    private static final Map<String, OptionType> OPTION_TYPES = new HashMap<>();

    private final CommandInfo commandInfo;
    private CommandRegistry commandRegistry;
    private SlashCommandEvent currentEvent;

    public Command() {
        OPTION_TYPES.put("str", OptionType.STRING);
        OPTION_TYPES.put("int", OptionType.INTEGER);
        OPTION_TYPES.put("bool", OptionType.BOOLEAN);
        OPTION_TYPES.put("user", OptionType.USER);
        OPTION_TYPES.put("channel", OptionType.CHANNEL);
        OPTION_TYPES.put("role", OptionType.ROLE);
        OPTION_TYPES.put("mentionable", OptionType.MENTIONABLE);
        OPTION_TYPES.put("number", OptionType.NUMBER);
        if (this.getClass().isAnnotationPresent(CommandInfo.class)) {
            this.commandInfo = this.getClass().getAnnotation(CommandInfo.class);
        } else {
            throw new IllegalStateException("The class " + this.getClass().getName() + " needs to have a CommandInfo" +
                    " annotation in order to get infos");
        }
    }

    public CommandData getCommandData() {
        return new CommandData(this.getCommandInfo().name(), this.getCommandInfo().description())
                .addOptions(this.parseOptions());
    }

    public void internallyExecute(SlashCommandEvent event) {
        this.currentEvent = event;

        if (this.commandInfo.autoCheckPermission()) {
            if(!this.commandInfo.permission().equals(Perms.ALL)) {
                if(this.commandInfo.permission().equals(Perms.OWNER)) {
                    if(!event.getMember().getRoles().contains(event.getGuild().getRoleById(Config.OWNER_ID))) {
                        event.reply("Vous ne pouvez pas executer cette commande ;)").queue();
                        return;
                    }
                }
            }
        }

        if (this instanceof CommandParent) {
            CommandParent parent = (CommandParent) this;

            if (this.commandInfo.autoManagingSubCommands()) {
                boolean result = parent.executeSubCommands(event);

                if (result) { // Has a sub-command been executed?
                    return; // Yes!
                }
            }
        }

        this.execute(event);
    }

    public List<OptionData> parseOptions() {
        List<OptionData> options = new ArrayList<>();
        String usage = this.commandInfo.usage();

        List<String> args = new ArrayList<>();
        for (int i = 0; i < usage.length(); i++) {
            int j = i + 1;

            for (; j <= usage.length(); j++) {
                Matcher matcher = OPTION_PATTERN.matcher(usage.substring(i, j));

                if (matcher.find()) {
                    args.add(usage.substring(i, j));

                    i = j;
                    break;
                }
            }
        }

        for (String arg : args) {
            Matcher matcher = OPTION_PATTERN.matcher(arg);
            matcher.find();

            boolean required = matcher.group(1).equals("<");
            String argName = matcher.group(2);
            String description = matcher.group(3) != null ? matcher.group(3).replaceAll("\\\\'", "'").replaceAll("\\\\\\\\", "\\\\") : "unknown";
            OptionType argType = OPTION_TYPES.getOrDefault(matcher.group(4).toLowerCase(), OptionType.STRING);

            options.add(new OptionData(argType, argName, description, required));
        }

        return options;
    }

    public abstract void execute(SlashCommandEvent event);

    public boolean isArgPresent(String name) {
        return this.currentEvent.getOption(name) != null;
    }

    @SuppressWarnings("unchecked")
    public <T> T readOptionalArg(String name, T defaultValue) {
        return (T) this.readArg(name, defaultValue.getClass(), defaultValue);
    }

    @SuppressWarnings("unchecked")
    public <T> T readRequiredArg(String name, Class<T> typeClass) {
        return (T) this.readArg(name, typeClass, null);
    }

    public Object readArg(String name, Class<?> typeClass, Object defaultValue) {
        ArgReader<?> argReader = this.commandRegistry.getArgReader(typeClass);

        try {
            Object value = argReader.read(this.currentEvent.getOption(name));

            if (value != null) {
                return value;
            } else if (defaultValue != null) {
                return defaultValue;
            } else {
                throw new IllegalStateException("Required arg " + name);
            }
        } catch (Exception e) {
            if (defaultValue != null) {
                return defaultValue;
            } else {
                throw new IllegalStateException("Error while reading arg " + name);
            }
        }
    }

    public void setCommandRegistry(CommandRegistry commandRegistry) {
        this.commandRegistry = commandRegistry;
    }

    public CommandInfo getCommandInfo() {
        return commandInfo;
    }

    public CommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    public SlashCommandEvent getCurrentEvent() {
        return currentEvent;
    }
}
