package fr.sothis.stats.commands.manager.arg.discord;

import fr.sothis.stats.commands.manager.arg.ArgReader;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class MessageChannelAR implements ArgReader<MessageChannel> {

    @Override
    public MessageChannel read(OptionMapping optionValue) {
        return optionValue.getAsMessageChannel();
    }
}
