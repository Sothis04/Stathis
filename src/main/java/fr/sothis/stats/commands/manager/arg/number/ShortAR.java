package fr.sothis.stats.commands.manager.arg.number;

import fr.sothis.stats.commands.manager.arg.ArgReader;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class ShortAR implements ArgReader<Short> {

    @Override
    public Short read(OptionMapping optionValue) {
        return (short) optionValue.getAsLong();
    }
}
