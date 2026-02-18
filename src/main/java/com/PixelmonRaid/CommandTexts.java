package com.PixelmonRaid;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

public final class CommandTexts {
    private CommandTexts() {}
    public static ITextComponent of(String s) { return new StringTextComponent(s); }
}
