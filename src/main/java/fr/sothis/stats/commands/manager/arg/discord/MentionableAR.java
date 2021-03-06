package fr.sothis.stats.commands.manager.arg.discord;

import fr.sothis.stats.commands.manager.arg.ArgReader;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class MentionableAR implements ArgReader<IMentionable> {

    @Override
    public IMentionable read(OptionMapping optionValue) {
        return optionValue.getAsMentionable();
    }
}
