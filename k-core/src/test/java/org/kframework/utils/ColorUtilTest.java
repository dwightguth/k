// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.utils;

import static org.junit.Assert.*;

import java.awt.Color;

import org.junit.Test;
import org.kframework.krun.ColorSetting;

public class ColorUtilTest {

    @Test
    public void testGetColor() {
        assertEquals("", new ColorUtil(ColorSetting.OFF, Color.BLACK).RgbToAnsi(Color.RED));
        assertEquals("\u001b[31m", new ColorUtil(ColorSetting.ON, Color.BLACK).RgbToAnsi(Color.RED));
        assertEquals("", new ColorUtil(ColorSetting.OFF, Color.RED).RgbToAnsi(Color.RED));
    }
}
