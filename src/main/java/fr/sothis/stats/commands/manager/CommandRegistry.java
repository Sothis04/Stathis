package fr.sothis.stats.commands.manager;

import fr.sothis.stats.commands.manager.arg.ArgReader;
import fr.sothis.stats.commands.manager.arg.BooleanAR;
import fr.sothis.stats.commands.manager.arg.CharAR;
import fr.sothis.stats.commands.manager.arg.StringAR;
import fr.sothis.stats.commands.manager.arg.discord.*;
import fr.sothis.stats.commands.manager.arg.number.*;
import fr.sothis.stats.commands.manager.utils.DiscordCommandConverter;
import fr.sothis.stats.config.Config;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.restaction.CommandCreateAction;
import net.dv8tion.jda.api.requests.restaction.CommandEditAction;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class CommandRegistry extends ListenerAdapter implements EventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandRegistry.class);

    private final JDA jda;
    private final Map<Class<?>, ArgReader<?>> argReaderRegistry;
    public final Map<String, Command> commands;
    private final DiscordCommandConverter commandConverter;

    public CommandRegistry(JDA jda) {
        this.jda = jda;
        this.argReaderRegistry = new HashMap<>();
        this.commands = new HashMap<>();
        this.commandConverter = new DiscordCommandConverter();
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        if (this.commands.containsKey(event.getName())) {
            Command command = this.commands.get(event.getName());
            command.internallyExecute(event);
        }
    }

    public void registerDefaults() {
        this.registerArgReader(byte.class, new ByteAR());
        this.registerArgReader(Byte.class, new ByteAR());
        this.registerArgReader(short.class, new ShortAR());
        this.registerArgReader(Short.class, new ShortAR());
        this.registerArgReader(int.class, new IntegerAR());
        this.registerArgReader(Integer.class, new IntegerAR());
        this.registerArgReader(long.class, new LongAR());
        this.registerArgReader(Long.class, new LongAR());
        this.registerArgReader(float.class, new FloatAR());
        this.registerArgReader(Float.class, new FloatAR());
        this.registerArgReader(double.class, new DoubleAR());
        this.registerArgReader(Double.class, new DoubleAR());

        this.registerArgReader(boolean.class, new BooleanAR());
        this.registerArgReader(Boolean.class, new BooleanAR());
        this.registerArgReader(char.class, new CharAR());
        this.registerArgReader(Character.class, new CharAR());
        this.registerArgReader(String.class, new StringAR());

        this.registerArgReader(GuildChannel.class, new GuildChannelAR());
        this.registerArgReader(Member.class, new MemberAR());
        this.registerArgReader(IMentionable.class, new MentionableAR());
        this.registerArgReader(MessageChannel.class, new MessageChannelAR());
        this.registerArgReader(Role.class, new RoleAR());
        this.registerArgReader(User.class, new UserAR());
    }

    public <T> void registerArgReader(Class<T> typeClass, ArgReader<T> argReaderClass) {
        this.argReaderRegistry.put(typeClass, argReaderClass);
    }

    public void registerAllCommandsIn(String packageName) {
        Reflections reflections = new Reflections(packageName);

        reflections.getSubTypesOf(Command.class).forEach(commandClass -> {
            try {
                if (commandClass.getPackage().getName().startsWith(packageName)
                        && !Modifier.isAbstract(commandClass.getModifiers()) && !Modifier.isInterface(commandClass.getModifiers())
                        && commandClass.isAnnotationPresent(CommandExist.class)) {
                    commandClass.getDeclaredConstructor(); // check if it has an empty constructor

                    this.registerCommand(commandClass);
                }
            } catch (NoSuchMethodException ignored) {
            }
        });
    }

    public void updateDiscord() {
        //LOGGER.info("Update Discord Commands");

        /**
         * CommandListUpdateAction CMDS = NovLab.getApi().updateCommands();
         *         Map<String, Command> cmds = new HashMap<>();
         *         List<String> prohibitedCMDS = new ArrayList<>(List.of("play", "setrole", "setstaff", "setchannel", "suggestion"));
         *         commands.forEach((s, command) -> {
         *             if(!prohibitedCMDS.contains(s)) {
         *                 cmds.put(s, command);
         *             }
         *         });
         *         CMDS.queue();
         */

        {
            List<net.dv8tion.jda.api.interactions.commands.Command> discordCommands = this.jda.retrieveCommands().complete();
            for (net.dv8tion.jda.api.interactions.commands.Command discordCommand : discordCommands) {
                if (this.commands.containsKey(discordCommand.getName())) {
                    Command command = this.commands.get(discordCommand.getName());
                    CommandData commandData = command.getCommandData();

                    //LOGGER.info("Command Update State " + (!commandData.toData().toString().equals(this.commandConverter.toCommandData(discordCommand).toData().toString())) + " " + command.getCommandInfo().name());

                    if (!commandData.toData().toString().equals(this.commandConverter.toCommandData(discordCommand).toData().toString())) {
                        //LOGGER.info("Command \"" + command.getCommandInfo().name() + "\" need to be edited!");

                        discordCommand.editCommand()
                                .setDescription(command.getCommandInfo().description())
                                .clearOptions()
                                .addOptions(commandData.getOptions())
                                .queue();
                    }
                } else {
                    String commandName = discordCommand.getName();
                    //LOGGER.info("Command \"" + commandName + "\" need to be deleted!");
                    discordCommand.delete().queue();
                    //LOGGER.info("Command \"" + commandName + "\" delete");
                }
            }

            this.commands.forEach((name, command) -> {
                if (discordCommands.stream().noneMatch(discordCommand -> discordCommand.getName().equals(name))) {
                    //LOGGER.info("Command \"" + command.getCommandInfo().name() + "\" need to be created!");
                    this.jda.upsertCommand(command.getCommandInfo().name(), command.getCommandInfo().description())
                            .addOptions(command.getCommandData().getOptions())
                            .queue();
                }
            });
        }

        for (String guildId : Config.SERVER_ID) {
            Guild guild = this.jda.getGuildById(guildId);

            if (guild != null) {
                List<net.dv8tion.jda.api.interactions.commands.Command> discordCommands = guild.retrieveCommands().complete();
                for (net.dv8tion.jda.api.interactions.commands.Command discordCommand : discordCommands) {
                    if (this.commands.containsKey(discordCommand.getName())) {
                        Command command = this.commands.get(discordCommand.getName());
                        CommandData commandData = command.getCommandData();

                        //LOGGER.info("Command Update State " + (!commandData.toData().toString().equals(this.commandConverter.toCommandData(discordCommand).toData().toString())) + " " + command.getCommandInfo().name());

                        if (!commandData.toData().toString().equals(this.commandConverter.toCommandData(discordCommand).toData().toString())) {
                            //LOGGER.info("Command \"" + command.getCommandInfo().name() + "\" need to be edited!");

                            CommandEditAction action = discordCommand.editCommand()
                                    .setDescription(command.getCommandInfo().description())
                                    .clearOptions();

                            if (command instanceof CommandParent) {
                                if (command.getCommandInfo().hasSubcommandGroups()) {
                                    action = action.addSubcommandGroups(commandData.getSubcommandGroups());
                                } else {
                                    action = action.addSubcommands(commandData.getSubcommands());
                                }
                            } else {
                                action = action.addOptions(commandData.getOptions());
                            }

                            action.queue();
                        }
                    } else {
                        String commandName = discordCommand.getName();
                        //LOGGER.info("Command \"" + commandName + "\" need to be deleted!");
                        discordCommand.delete().queue();
                        //LOGGER.info("Command \"" + commandName + "\" delete");
                    }
                }

                this.commands.forEach((name, command) -> {
                    if (discordCommands.stream().noneMatch(discordCommand -> discordCommand.getName().equals(name))) {
                        //LOGGER.info("Command \"" + command.getCommandInfo().name() + "\" need to be created!");

                        final AtomicReference<CommandCreateAction> action = new AtomicReference<>(guild.upsertCommand(command.getCommandInfo().name(), command.getCommandInfo().description()));

                        if (command instanceof CommandParent) {
                            if (command.getCommandInfo().hasSubcommandGroups()) {
                                command.getCommandData().getSubcommandGroups().forEach(subcommandGroupData -> {
                                    action.set(action.get().addSubcommandGroups(subcommandGroupData));
                                });
                            } else {
                                command.getCommandData().getSubcommands().forEach(subcommandData -> {
                                    action.set(action.get().addSubcommands(subcommandData));
                                });
                            }
                        } else {
                            command.getCommandData().getOptions().forEach(optionData -> {
                                action.set(action.get().addOptions(optionData));
                            });
                        }

                        action.get().queue();
                    }
                });
            }
        }
    }

    public void registerCommand(Class<? extends Command> clazz) {
        try {
            Command command = clazz.getDeclaredConstructor().newInstance();
            command.setCommandRegistry(this);

            this.commands.put(command.getCommandInfo().name(), command);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalArgumentException("Couldn't instantiate Command class " + clazz.getName(), e);
        }
    }


    @SuppressWarnings("all")
    public <T> ArgReader<T> getArgReader(T typeClass) {
        return (ArgReader<T>) this.argReaderRegistry.get(typeClass);
    }
}
